/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.systest.core.brokerj;

import static org.apache.qpid.systest.core.brokerj.SpawnQpidBrokerAdmin.SYSTEST_PROPERTY_BROKERJ_DEPENDECIES;
import static org.apache.qpid.systest.core.brokerj.SpawnQpidBrokerAdmin.SYSTEST_PROPERTY_BUILD_CLASSPATH_FILE;
import static org.apache.qpid.systest.core.brokerj.SpawnQpidBrokerAdmin.SYSTEST_PROPERTY_INITIAL_CONFIGURATION_LOCATION;
import static org.apache.qpid.systest.core.brokerj.SpawnQpidBrokerAdmin.SYSTEST_PROPERTY_JAVA8_EXECUTABLE;
import static org.apache.qpid.systest.core.brokerj.SpawnQpidBrokerAdmin.SYSTEST_PROPERTY_VIRTUALHOST_BLUEPRINT;
import static org.apache.qpid.systest.core.brokerj.SpawnQpidBrokerAdmin.SYSTEST_PROPERTY_VIRTUALHOSTNODE_TYPE;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

import java.io.File;
import java.util.Hashtable;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.junit.Test;

import org.apache.qpid.systest.core.BrokerAdmin;
import org.apache.qpid.systest.core.BrokerAdminException;

public class SpawnQpidBrokerAdminTest
{
    @Test
    public void startBroker() throws Exception
    {
        assumeBrokerCanBeStarted();

        SpawnQpidBrokerAdmin spawnQpidBrokerAdmin = new SpawnQpidBrokerAdmin();
        try
        {
            spawnQpidBrokerAdmin.start(SpawnQpidBrokerAdminTest.class);
        }
        finally
        {
            spawnQpidBrokerAdmin.shutdown();
        }
    }

    @Test
    public void createVirtualHosts() throws Exception
    {
        assumeBrokerCanBeStarted();
        assumeVirtualHostCanBeCreated();

        SpawnQpidBrokerAdmin spawnQpidBrokerAdmin = new SpawnQpidBrokerAdmin();
        try
        {
            spawnQpidBrokerAdmin.start(SpawnQpidBrokerAdminTest.class);

            spawnQpidBrokerAdmin.createVirtualHost("test");
            try
            {
                spawnQpidBrokerAdmin.createVirtualHost("test2");
                fail("The creation of second default virtual host should fail");
            }
            catch(BrokerAdminException e)
            {
                // pass
            }
            finally
            {
                spawnQpidBrokerAdmin.deleteVirtualHost("test");
            }
        }
        finally
        {
            spawnQpidBrokerAdmin.shutdown();
        }
    }

    @Test
    public void createVirtualHostAndConnect() throws Exception
    {
        assumeBrokerCanBeStarted();
        assumeVirtualHostCanBeCreated();

        SpawnQpidBrokerAdmin spawnQpidBrokerAdmin = new SpawnQpidBrokerAdmin();
        try
        {
            spawnQpidBrokerAdmin.start(SpawnQpidBrokerAdminTest.class);

            final String virtualHostName = "test";
            spawnQpidBrokerAdmin.createVirtualHost(virtualHostName);
            try
            {
                Connection connection = getConnection(virtualHostName, spawnQpidBrokerAdmin);
                try
                {
                    connection.createSession(true, Session.SESSION_TRANSACTED).close();
                }
                finally
                {
                    connection.close();
                }
            }
            finally
            {
                spawnQpidBrokerAdmin.deleteVirtualHost(virtualHostName);
            }
        }
        finally
        {
            spawnQpidBrokerAdmin.shutdown();
        }
    }

    @Test
    public void deleteVirtualHost() throws Exception
    {
        assumeBrokerCanBeStarted();
        assumeVirtualHostCanBeCreated();

        SpawnQpidBrokerAdmin spawnQpidBrokerAdmin = new SpawnQpidBrokerAdmin();
        try
        {
            spawnQpidBrokerAdmin.start(SpawnQpidBrokerAdminTest.class);

            // create and delete VH twice
            spawnQpidBrokerAdmin.createVirtualHost("test");
            spawnQpidBrokerAdmin.deleteVirtualHost("test");

            // if previous deletion failed, than creation should fail as well
            spawnQpidBrokerAdmin.createVirtualHost("test");
            spawnQpidBrokerAdmin.deleteVirtualHost("test");
        }
        finally
        {
            spawnQpidBrokerAdmin.shutdown();
        }
    }

    @Test
    public void restartVirtualHost() throws Exception
    {
        assumeBrokerCanBeStarted();
        assumeVirtualHostCanBeCreated();

        String virtualHostName = "test";
        SpawnQpidBrokerAdmin spawnQpidBrokerAdmin = new SpawnQpidBrokerAdmin();
        try
        {
            spawnQpidBrokerAdmin.start(SpawnQpidBrokerAdminTest.class);

            try
            {
                spawnQpidBrokerAdmin.restartVirtualHost(virtualHostName);
                fail("Virtual host restart should fail as virtual host is no created yet");
            }
            catch (BrokerAdminException e)
            {
                // pass
            }

            spawnQpidBrokerAdmin.createVirtualHost(virtualHostName);
            try
            {
                Connection connection = getConnection(virtualHostName, spawnQpidBrokerAdmin);
                try
                {
                    Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
                    spawnQpidBrokerAdmin.restartVirtualHost(virtualHostName);

                    try
                    {
                        session.commit();
                        fail("Session should be closed and commit should not be permitted on closed session");
                    }
                    catch (JMSException e)
                    {
                        // pass
                    }
                }
                finally
                {
                    connection.close();
                }

                Connection connection2 = getConnection(virtualHostName, spawnQpidBrokerAdmin);
                try
                {
                    connection2.createSession(true, Session.SESSION_TRANSACTED).commit();
                }
                finally
                {
                    connection2.close();
                }
            }
            finally
            {
                spawnQpidBrokerAdmin.deleteVirtualHost(virtualHostName);
            }
        }
        finally
        {
            spawnQpidBrokerAdmin.shutdown();
        }
    }

    private Connection getConnection(String virtualHostName, BrokerAdmin brokerAdmin) throws JMSException
    {
        final Hashtable<Object, Object> initialContextEnvironment = new Hashtable<>();
        initialContextEnvironment.put(Context.INITIAL_CONTEXT_FACTORY,
                                      "org.apache.qpid.jndi.PropertiesFileInitialContextFactory");
        final String factoryName = "connectionFactory";
        String urlTemplate = "amqp://:@%s/%s?brokerlist='tcp://localhost:%d?failover='nofailover''";
        String url = String.format(urlTemplate,
                                   "system_test",
                                   virtualHostName,
                                   brokerAdmin.getBrokerAddress(BrokerAdmin.PortType.AMQP).getPort());
        initialContextEnvironment.put("connectionfactory." + factoryName, url);
        try
        {
            InitialContext initialContext = new InitialContext(initialContextEnvironment);
            try
            {
                ConnectionFactory factory = (ConnectionFactory) initialContext.lookup(factoryName);
                return factory.createConnection(brokerAdmin.getValidUsername(), brokerAdmin.getValidPassword());
            }
            finally
            {
                initialContext.close();
            }
        }
        catch (NamingException e)
        {
            throw new RuntimeException("Unexpected exception on connection lookup", e);
        }
    }

    private void assumeVirtualHostCanBeCreated()
    {
        assumeThat(String.format("Virtual host type property (%s) is not set", SYSTEST_PROPERTY_VIRTUALHOSTNODE_TYPE),
                   System.getProperty(SYSTEST_PROPERTY_VIRTUALHOSTNODE_TYPE), is(notNullValue()));
        assumeThat(String.format("Virtual host blueprint property (%s) is not set",
                                 SYSTEST_PROPERTY_VIRTUALHOST_BLUEPRINT),
                   System.getProperty(SYSTEST_PROPERTY_VIRTUALHOST_BLUEPRINT), is(notNullValue()));
    }

    private void assumeBrokerCanBeStarted()
    {
        assumeThat(String.format("Java 8 executable property (%s) is not set", SYSTEST_PROPERTY_JAVA8_EXECUTABLE),
                   System.getProperty(SYSTEST_PROPERTY_JAVA8_EXECUTABLE), is(notNullValue()));
        assumeThat("Java 8 executable does not exist",
                   new File(System.getProperty(SYSTEST_PROPERTY_JAVA8_EXECUTABLE)).exists(), is(equalTo(true)));
        assumeThat(String.format("Broker-J classpath property (%s) is not set", SYSTEST_PROPERTY_BUILD_CLASSPATH_FILE),
                   System.getProperty(SYSTEST_PROPERTY_BUILD_CLASSPATH_FILE), is(notNullValue()));
        assumeThat(String.format("Broker dependencies property (%s) is not set", SYSTEST_PROPERTY_BROKERJ_DEPENDECIES),
                   System.getProperty(SYSTEST_PROPERTY_BROKERJ_DEPENDECIES), is(notNullValue()));
        assumeThat(String.format("Broker-J initial configuration property (%s) is not set", SYSTEST_PROPERTY_INITIAL_CONFIGURATION_LOCATION),
                   System.getProperty(SYSTEST_PROPERTY_INITIAL_CONFIGURATION_LOCATION), is(notNullValue()));
    }
}