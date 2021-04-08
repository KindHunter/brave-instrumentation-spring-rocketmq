package com.cxq56.instrumentation.rocketmq.configuration;

import brave.Tracing;
import com.cxq56.instrumentation.rocketmq.trace.ConsumeMessageTraceHook;
import com.cxq56.instrumentation.rocketmq.trace.SendMessageTraceHook;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.spring.autoconfigure.ListenerContainerConfiguration;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.async.TraceableExecutorService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * @program: SpringBoot_Dubbo_Example
 * @description:
 * @author: wangJun
 * @create: 2021-03-12 15:31
 **/
@Configuration
@AutoConfigureAfter(value = {ListenerContainerConfiguration.class, RocketMQAutoConfiguration.class, TraceAutoConfiguration.class})
public class RocketMqTracerConfiguration implements BeanFactoryAware, SmartInitializingSingleton, BeanPostProcessor {

    private final static Logger log = LoggerFactory.getLogger(RocketMqTracerConfiguration.class);

    private BeanFactory beanFactory;

    @Autowired
    Tracing tracing;

    @Autowired(required = false)
    RocketMQTemplate rocketMQTemplate;

    @Autowired
    ConsumeMessageTraceHook consumeMessageTraceHook;



    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Bean
    public ConsumeMessageTraceHook getConsumeMessageTraceHook(){
        ConsumeMessageTraceHook consumeMessageTraceHook = new ConsumeMessageTraceHook();

        consumeMessageTraceHook.setTracing(tracing);

        return consumeMessageTraceHook;
    }


    @Override
    public void afterSingletonsInstantiated() {
        if (rocketMQTemplate != null) {
            SendMessageTraceHook sendMessageTraceHook = new SendMessageTraceHook();

            sendMessageTraceHook.setTracing(tracing);

            DefaultMQProducerImpl defaultMQProducerImpl = rocketMQTemplate.getProducer().getDefaultMQProducerImpl();

            defaultMQProducerImpl.registerSendMessageHook(sendMessageTraceHook);

            TraceableExecutorService traceableExecutorService = new TraceableExecutorService(beanFactory, defaultMQProducerImpl.getAsyncSenderExecutor());

            defaultMQProducerImpl.setAsyncSenderExecutor(traceableExecutorService);

            log.debug("defaultMQProducerImpl add SendMessageTraceHook");

        }
    }




    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {

        if (bean instanceof DefaultRocketMQListenerContainer) {
            DefaultRocketMQListenerContainer rocketMQListenerContainer = (DefaultRocketMQListenerContainer)bean;

            rocketMQListenerContainer
                    .getConsumer()
                    .getDefaultMQPushConsumerImpl()
                    .registerConsumeMessageHook(this.consumeMessageTraceHook);

            log.debug("beanName:{}, add ConsumeMessageHook", beanName);

        }


        return bean;
    }

}
