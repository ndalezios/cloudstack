// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.async;

import javax.inject.Inject;
import junit.framework.TestCase;

import org.apache.cloudstack.framework.messagebus.MessageBus;
import org.apache.cloudstack.framework.messagebus.PublishScope;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.cloud.async.AsyncJobManager;
import com.cloud.cluster.ClusterManager;
import com.cloud.utils.Predicate;
import com.cloud.utils.component.ComponentContext;
import com.cloud.utils.db.Transaction;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations="classpath:/AsyncJobTestContext.xml")
public class TestAsyncJobManager extends TestCase {

    @Inject AsyncJobManager asyncMgr;
    @Inject ClusterManager clusterMgr;
    @Inject MessageBus messageBus;
    
    @Before                                                  
    public void setUp() {
    	ComponentContext.initComponentsLifeCycle();
    	Mockito.when(clusterMgr.getManagementNodeId()).thenReturn(1L);
    	
    	Transaction.open("dummy");                           
    }                                                        
                                                             
    @After                                                   
    public void tearDown() {                                 
    	Transaction.currentTxn().close();                    
    }                                                        
    
    @Test
    public void testWaitAndCheck() {
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				for(int i = 0; i < 2; i++) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
					}
					System.out.println("Publish wakeup message");
					messageBus.publish(null, "VM", PublishScope.GLOBAL, null);
				}
			}
		});
		thread.start();
    	
    	asyncMgr.waitAndCheck(new String[] {"VM"}, 5000L, 10000L, new Predicate() {
    		public boolean checkCondition() {
    			System.out.println("Check condition to exit");
    			return false;
    		}
    	});
    	
    	try {
    		thread.join();
    	} catch(InterruptedException e) {
    	}
    }
}
