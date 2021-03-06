/*
 * Copyright 2015 Groupon, Inc
 * Copyright 2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.plugin.api.notification;

import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.plugin.TestUtils;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillLogService;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestPluginTenantConfigurableConfigurationHandler {

    private TenantUserApi tenantUserApi;
    private PluginTenantConfigurableConfigurationHandlerTest configurationHandler;

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        tenantUserApi = Mockito.mock(TenantUserApi.class);

        final Account account = TestUtils.buildAccount(Currency.BTC, "US");
        final OSGIKillbillAPI killbillAPI = TestUtils.buildOSGIKillbillAPI(account, TestUtils.buildPayment(account.getId(), account.getPaymentMethodId(), account.getCurrency()), null);
        Mockito.when(killbillAPI.getTenantUserApi()).thenReturn(tenantUserApi);
        final OSGIKillbillLogService logService = TestUtils.buildLogService();

        configurationHandler = new PluginTenantConfigurableConfigurationHandlerTest("test", killbillAPI, logService);
        configurationHandler.setDefaultConfigurable("DEFAULT");
    }

    @Test(groups = "fast")
    public void testConfigurationForTenant() throws Exception {
        final UUID configuredTenant = UUID.randomUUID();
        final UUID otherTenant = UUID.randomUUID();
        mockTenantKvs(configuredTenant, ImmutableList.<String>of("key=CONFIGURED_TENANT"), otherTenant, ImmutableList.<String>of());

        final String initialConfigurableForConfiguredTenant = configurationHandler.getConfigurable(configuredTenant);
        Assert.assertEquals(initialConfigurableForConfiguredTenant, "CONFIGURED_TENANT");
        final String initialConfigurableForOtherTenant = configurationHandler.getConfigurable(otherTenant);
        Assert.assertEquals(initialConfigurableForOtherTenant, "DEFAULT");
        Assert.assertEquals(configurationHandler.getConfigurable(UUID.randomUUID()), "DEFAULT");
        Assert.assertEquals(configurationHandler.getConfigurable(null), "DEFAULT");

        // Configure the other tenant explicitly
        mockTenantKvs(configuredTenant, ImmutableList.<String>of("key=CONFIGURED_TENANT"), otherTenant, ImmutableList.<String>of("key=OTHER_CONFIGURED_TENANT"));
        configurationHandler.configure(otherTenant);

        final String subsequentConfigurableForConfiguredTenant = configurationHandler.getConfigurable(configuredTenant);
        Assert.assertEquals(subsequentConfigurableForConfiguredTenant, "CONFIGURED_TENANT");
        final String subsequentConfigurableForOtherTenant = configurationHandler.getConfigurable(otherTenant);
        Assert.assertEquals(subsequentConfigurableForOtherTenant, "OTHER_CONFIGURED_TENANT");
        Assert.assertEquals(configurationHandler.getConfigurable(UUID.randomUUID()), "DEFAULT");
        Assert.assertEquals(configurationHandler.getConfigurable(null), "DEFAULT");
    }

    private void mockTenantKvs(final UUID kbTenantIdA, final List<String> tenantKvsA, final UUID kbTenantIdB, final List<String> tenantKvsB) throws TenantApiException {
        Mockito.when(tenantUserApi.getTenantValuesForKey(Mockito.anyString(), Mockito.<TenantContext>any())).thenAnswer(new Answer<List<String>>() {
            @Override
            public List<String> answer(final InvocationOnMock invocation) throws Throwable {
                if (!"PLUGIN_CONFIG_test".equals(invocation.getArguments()[0])) {
                    return ImmutableList.<String>of();
                }

                final TenantContext context = (TenantContext) invocation.getArguments()[1];
                if (kbTenantIdA.equals(context.getTenantId())) {
                    return tenantKvsA;
                } else if (kbTenantIdB.equals(context.getTenantId())) {
                    return tenantKvsB;
                } else {
                    return ImmutableList.<String>of();
                }
            }
        });
    }

    private static final class PluginTenantConfigurableConfigurationHandlerTest extends PluginTenantConfigurableConfigurationHandler<String> {

        public PluginTenantConfigurableConfigurationHandlerTest(final String pluginName, final OSGIKillbillAPI osgiKillbillAPI, final OSGIKillbillLogService osgiKillbillLogService) {
            super(pluginName, osgiKillbillAPI, osgiKillbillLogService);
        }

        @Override
        protected String createConfigurable(final Properties properties) {
            return properties.getProperty("key");
        }
    }
}
