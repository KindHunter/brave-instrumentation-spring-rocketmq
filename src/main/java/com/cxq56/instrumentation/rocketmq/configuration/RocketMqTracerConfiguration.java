package com.cxq56.instrumentation.rocketmq.configuration;

import brave.Tracing;
import com.cxq56.instrumentation.rocketmq.trace.ConsumeMessageTraceHook;
import com.cxq56.instrumentation.rocketmq.trace.SendMessageTraceHook;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.spring.autoconfigure.ListenerContainerConfiguration;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.cloud.sleuth.instrument.async.TraceableExecutorService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * @program: SpringBoot_Dubbo_Example
 * @description:
 * @author: wangJun
 * @create: 2021-03-12 15:31
 **/
@Configuration
@AutoConfigureAfter(value = {ListenerContainerConfiguration.class, RocketMQAutoConfiguration.class})
public class RocketMqTracerConfiguration implements ApplicationContextAware, BeanFactoryAware, CommandLineRunner, SmartInitializingSingleton {

    private ApplicationContext applicationContext;

    private BeanFactory beanFactory;

    @Autowired
    Tracing tracing;

    @Autowired(required = false)
    RocketMQTemplate rocketMQTemplate;


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
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
        }
    }



    @Override
    public void run(String... args) throws Exception {
        Map<String, DefaultRocketMQListenerContainer> beanMap = applicationContext.getBeansOfType(DefaultRocketMQListenerContainer.class);

        ConsumeMessageTraceHook consumeMessageTraceHook = new ConsumeMessageTraceHook();

        consumeMessageTraceHook.setTracing(tracing);

        beanMap.forEach((beanName, listenerContainer) -> {
            listenerContainer.getConsumer()
                    .getDefaultMQPushConsumerImpl()
                    .registerConsumeMessageHook(consumeMessageTraceHook);
        });
    }



}
