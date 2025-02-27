/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.core.server.balancing;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.balancing.caches.Cache;
import org.apache.activemq.artemis.core.server.balancing.policies.Policy;
import org.apache.activemq.artemis.core.server.balancing.pools.Pool;
import org.apache.activemq.artemis.core.server.balancing.targets.Target;
import org.apache.activemq.artemis.core.server.balancing.targets.TargetKey;
import org.apache.activemq.artemis.core.server.balancing.targets.TargetKeyResolver;
import org.apache.activemq.artemis.core.server.balancing.targets.TargetResult;
import org.apache.activemq.artemis.core.server.balancing.transformer.KeyTransformer;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.regex.Pattern;

public class BrokerBalancer implements ActiveMQComponent {
   private static final Logger logger = Logger.getLogger(BrokerBalancer.class);


   public static final String CLIENT_ID_PREFIX = ActiveMQDefaultConfiguration.DEFAULT_INTERNAL_NAMING_PREFIX + "balancer.client.";

   private final String name;

   private final TargetKey targetKey;

   private final TargetKeyResolver targetKeyResolver;

   private final TargetResult localTarget;

   private volatile Pattern localTargetFilter;

   private final Pool pool;

   private final Policy policy;

   private final KeyTransformer transformer;

   private final Cache cache;

   private volatile boolean started = false;

   public String getName() {
      return name;
   }

   public TargetKey getTargetKey() {
      return targetKey;
   }

   public Target getLocalTarget() {
      return localTarget.getTarget();
   }

   public String getLocalTargetFilter() {
      return localTargetFilter != null ? localTargetFilter.pattern() : null;
   }

   public Pool getPool() {
      return pool;
   }

   public Policy getPolicy() {
      return policy;
   }

   public Cache getCache() {
      return cache;
   }

   @Override
   public boolean isStarted() {
      return started;
   }


   public BrokerBalancer(final String name,
                         final TargetKey targetKey,
                         final String targetKeyFilter,
                         final Target localTarget,
                         final String localTargetFilter,
                         final Cache cache,
                         final Pool pool,
                         final Policy policy,
                         KeyTransformer transformer) {
      this.name = name;

      this.targetKey = targetKey;

      this.transformer = transformer;

      this.targetKeyResolver = new TargetKeyResolver(targetKey, targetKeyFilter);

      this.localTarget = new TargetResult(localTarget);

      this.localTargetFilter = localTargetFilter != null ? Pattern.compile(localTargetFilter) : null;

      this.pool = pool;

      this.policy = policy;

      this.cache = cache;
   }

   @Override
   public void start() throws Exception {
      if (cache != null) {
         cache.start();
      }

      if (pool != null) {
         pool.start();
      }

      started = true;
   }

   @Override
   public void stop() throws Exception {
      started = false;

      if (pool != null) {
         pool.stop();
      }

      if (cache != null) {
         cache.stop();
      }
   }

   public TargetResult getTarget(Connection connection, String clientID, String username) {
      if (clientID != null && clientID.startsWith(BrokerBalancer.CLIENT_ID_PREFIX)) {
         if (logger.isDebugEnabled()) {
            logger.debug("The clientID [" + clientID + "] starts with BrokerBalancer.CLIENT_ID_PREFIX");
         }

         return localTarget;
      }

      return getTarget(targetKeyResolver.resolve(connection, clientID, username));
   }

   public TargetResult getTarget(String key) {

      if (this.localTargetFilter != null && this.localTargetFilter.matcher(transform(key)).matches()) {
         if (logger.isDebugEnabled()) {
            logger.debug("The " + targetKey + "[" + key + "] matches the localTargetFilter " + localTargetFilter.pattern());
         }

         return localTarget;
      }

      if (pool == null) {
         return TargetResult.REFUSED_USE_ANOTHER_RESULT;
      }

      TargetResult result = null;

      if (cache != null) {
         String nodeId = cache.get(key);

         if (logger.isDebugEnabled()) {
            logger.debug("The cache returns target [" + nodeId + "] for " + targetKey + "[" + key + "]");
         }

         if (nodeId != null) {
            Target target = pool.getReadyTarget(nodeId);
            if (target != null) {
               if (logger.isDebugEnabled()) {
                  logger.debug("The target [" + nodeId + "] is ready for " + targetKey + "[" + key + "]");
               }

               return new TargetResult(target);
            }

            if (logger.isDebugEnabled()) {
               logger.debug("The target [" + nodeId + "] is not ready for " + targetKey + "[" + key + "]");
            }
         }
      }

      List<Target> targets = pool.getTargets();

      Target target = policy.selectTarget(targets, key);

      if (logger.isDebugEnabled()) {
         logger.debug("The policy selects [" + target + "] from " + targets + " for " + targetKey + "[" + key + "]");
      }

      if (target != null) {
         result = new TargetResult(target);
         if (cache != null) {
            if (logger.isDebugEnabled()) {
               logger.debug("Caching " + targetKey + "[" + key + "] for [" + target + "]");
            }
            cache.put(key, target.getNodeID());
         }
      }

      return result != null ? result : TargetResult.REFUSED_UNAVAILABLE_RESULT;
   }

   public void setLocalTargetFilter(String regExp) {
      if (regExp == null || regExp.trim().isEmpty()) {
         this.localTargetFilter = null;
      } else {
         this.localTargetFilter = Pattern.compile(regExp);
      }
   }

   public TargetKeyResolver getTargetKeyResolver() {
      return targetKeyResolver;
   }

   private String transform(String key) {
      String result = key;
      if (transformer != null) {
         result = transformer.transform(key);
         if (logger.isDebugEnabled()) {
            logger.debug("Key: " + key + ", transformed to " + result);
         }
      }
      return result;
   }
}
