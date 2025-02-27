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

package org.apache.activemq.artemis.core.server.balancing.transformer;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.activemq.artemis.core.server.balancing.BrokerBalancer;

public class TransformerFactoryResolver {
   private static TransformerFactoryResolver instance;

   public static TransformerFactoryResolver getInstance() {
      if (instance == null) {
         instance = new TransformerFactoryResolver();
      }
      return instance;
   }

   private final Map<String, TransformerFactory> factories = new HashMap<>();

   private TransformerFactoryResolver() {
      factories.put(ConsistentHashModulo.NAME, () -> new ConsistentHashModulo());
      loadFactories(); // let service loader override
   }

   public TransformerFactory resolve(String policyName) throws ClassNotFoundException {
      TransformerFactory factory = factories.get(policyName);
      if (factory == null) {
         throw new ClassNotFoundException("No TransformerFactory found for " + policyName);
      }
      return factory;
   }

   private void loadFactories() {
      ServiceLoader<TransformerFactory> serviceLoader = ServiceLoader.load(
         TransformerFactory.class, BrokerBalancer.class.getClassLoader());
      for (TransformerFactory factory : serviceLoader) {
         factories.put(keyFromClassName(factory.getClass().getName()), factory);
      }
   }

   String keyFromClassName(String name) {
      return name.substring(0, name.indexOf("TransformerFactory"));
   }
}
