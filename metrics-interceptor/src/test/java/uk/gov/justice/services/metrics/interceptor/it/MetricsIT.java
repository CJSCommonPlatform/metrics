package uk.gov.justice.services.metrics.interceptor.it;


import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static uk.gov.justice.services.core.annotation.Component.EVENT_LISTENER;
import static uk.gov.justice.services.core.interceptor.InterceptorContext.interceptorContextWithInput;
import static uk.gov.justice.services.test.utils.core.messaging.JsonEnvelopeBuilder.envelope;
import static uk.gov.justice.services.test.utils.core.messaging.MetadataBuilderFactory.metadataWithRandomUUID;

import uk.gov.justice.services.cdi.LoggerProducer;
import uk.gov.justice.services.common.configuration.GlobalValueProducer;
import uk.gov.justice.services.common.converter.ObjectToJsonValueConverter;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.accesscontrol.AccessControlFailureMessageGenerator;
import uk.gov.justice.services.core.accesscontrol.AllowAllPolicyEvaluator;
import uk.gov.justice.services.core.accesscontrol.DefaultAccessControlService;
import uk.gov.justice.services.core.accesscontrol.PolicyEvaluator;
import uk.gov.justice.services.core.annotation.Adapter;
import uk.gov.justice.services.core.annotation.FrameworkComponent;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.annotation.ServiceComponent;
import uk.gov.justice.services.core.dispatcher.DispatcherCache;
import uk.gov.justice.services.core.dispatcher.EmptySystemUserProvider;
import uk.gov.justice.services.core.dispatcher.ServiceComponentObserver;
import uk.gov.justice.services.core.dispatcher.SystemUserUtil;
import uk.gov.justice.services.core.envelope.EnvelopeValidationExceptionHandlerProducer;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.extension.BeanInstantiater;
import uk.gov.justice.services.core.extension.ServiceComponentScanner;
import uk.gov.justice.services.core.interceptor.InterceptorCache;
import uk.gov.justice.services.core.interceptor.InterceptorChainEntry;
import uk.gov.justice.services.core.interceptor.InterceptorChainEntryProvider;
import uk.gov.justice.services.core.interceptor.InterceptorChainObserver;
import uk.gov.justice.services.core.interceptor.InterceptorChainProcessor;
import uk.gov.justice.services.core.interceptor.InterceptorChainProcessorProducer;
import uk.gov.justice.services.core.json.JsonSchemaLoader;
import uk.gov.justice.services.core.requester.RequesterProducer;
import uk.gov.justice.services.core.sender.SenderProducer;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.jms.DefaultEnvelopeConverter;
import uk.gov.justice.services.messaging.jms.DefaultJmsEnvelopeSender;
import uk.gov.justice.services.messaging.spi.JsonEnvelopeProvider;
import uk.gov.justice.services.metrics.interceptor.IndividualActionMetricsInterceptor;
import uk.gov.justice.services.metrics.interceptor.MetricRegistryProducer;
import uk.gov.justice.services.metrics.interceptor.TotalActionMetricsInterceptor;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.openejb.jee.Application;
import org.apache.openejb.jee.WebApp;
import org.apache.openejb.junit.ApplicationComposer;
import org.apache.openejb.testing.Classes;
import org.apache.openejb.testing.Module;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(ApplicationComposer.class)
@FrameworkComponent("CORE_TEST")
@Adapter(EVENT_LISTENER)
public class MetricsIT {

    private static final String EVENT_ABC = "event-abc";
    private static final String EVENT_BCD = "event-bcd";

    @Inject
    private InterceptorChainProcessor interceptorChainProcessor;

    private MBeanServer server = ManagementFactory.getPlatformMBeanServer();

    @Module
    @Classes(cdi = true, value = {
            EventHandler.class,
            ServiceComponentScanner.class,
            RequesterProducer.class,
            ServiceComponentObserver.class,

            InterceptorChainProcessorProducer.class,
            InterceptorChainProcessor.class,
            InterceptorCache.class,
            InterceptorChainObserver.class,
            EventListenerInterceptorChainProvider.class,

            SenderProducer.class,
            DefaultJmsEnvelopeSender.class,
            DefaultEnvelopeConverter.class,

            StringToJsonObjectConverter.class,
            ObjectToJsonValueConverter.class,
            ObjectMapper.class,
            Enveloper.class,

            AccessControlFailureMessageGenerator.class,
            AllowAllPolicyEvaluator.class,
            DefaultAccessControlService.class,
            DispatcherCache.class,
            PolicyEvaluator.class,
            LoggerProducer.class,
            EmptySystemUserProvider.class,
            BeanInstantiater.class,
            MetricRegistryProducer.class,
            TotalActionMetricsInterceptor.class,
            IndividualActionMetricsInterceptor.class,
            SystemUserUtil.class,
            UtcClock.class,

            EnvelopeValidationExceptionHandlerProducer.class,
            GlobalValueProducer.class,
            JsonSchemaLoader.class,
            JsonEnvelopeProvider.class
    })
    public WebApp war() {
        return new WebApp()
                .contextRoot("core-test")
                .addServlet("TestApp", Application.class.getName());
    }

    @Test
    public void shouldExposeTotalComponentMetrics() throws Exception {

        final JsonEnvelope jsonEnvelope = envelope()
                .with(metadataWithRandomUUID(EVENT_ABC))
                .build();

        interceptorChainProcessor.process(interceptorContextWithInput(jsonEnvelope));
        interceptorChainProcessor.process(interceptorContextWithInput(jsonEnvelope));

        final ObjectName metricsObjectName = new ObjectName("uk.gov.justice.metrics:name=test-component.action.total");

        final Object count = server.getAttribute(metricsObjectName, "Count");
        final Object mean = server.getAttribute(metricsObjectName, "Mean");
        final Object max = server.getAttribute(metricsObjectName, "Max");
        final Object min = server.getAttribute(metricsObjectName, "Min");

        assertThat(count, is(2L));
        assertThat(mean, not(nullValue()));
        assertThat(max, not(nullValue()));
        assertThat(min, not(nullValue()));

    }

    @Test
    public void shouldExposeMetricsPerMessageName() throws Exception {

        final JsonEnvelope jsonEnvelope_1 = envelope()
                .with(metadataWithRandomUUID(EVENT_ABC))
                .build();
        final JsonEnvelope jsonEnvelope_2 = envelope()
                .with(metadataWithRandomUUID(EVENT_BCD))
                .build();

        interceptorChainProcessor.process(interceptorContextWithInput(jsonEnvelope_1));
        interceptorChainProcessor.process(interceptorContextWithInput(jsonEnvelope_2));
        interceptorChainProcessor.process(interceptorContextWithInput(jsonEnvelope_1));

        final ObjectName metricsAbcObjectName = new ObjectName("uk.gov.justice.metrics:name=test-component.action.event-abc");
        final ObjectName metricsBcdObjectName = new ObjectName("uk.gov.justice.metrics:name=test-component.action.event-bcd");

        final Object countAbc = server.getAttribute(metricsAbcObjectName, "Count");
        final Object countBcd = server.getAttribute(metricsBcdObjectName, "Count");

        assertThat(countAbc, is(2L));
        assertThat(countBcd, is(1L));
    }

    @ServiceComponent(EVENT_LISTENER)
    @ApplicationScoped
    public static class EventHandler {

        @Handles(EVENT_ABC)
        public void handleAbc(final JsonEnvelope envelope) {

        }

        @Handles(EVENT_BCD)
        public void handleBcd(final JsonEnvelope envelope) {

        }

    }

    public static class EventListenerInterceptorChainProvider implements InterceptorChainEntryProvider {

        @Override
        public String component() {
            return EVENT_LISTENER;
        }

        @Override
        public List<InterceptorChainEntry> interceptorChainTypes() {
            final List<InterceptorChainEntry> interceptorChainTypes = new ArrayList<>();
            interceptorChainTypes.add(new InterceptorChainEntry(1, TotalActionMetricsInterceptor.class));
            interceptorChainTypes.add(new InterceptorChainEntry(2, IndividualActionMetricsInterceptor.class));
            return interceptorChainTypes;
        }
    }
}
