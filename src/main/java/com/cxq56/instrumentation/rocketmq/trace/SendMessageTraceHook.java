package com.cxq56.instrumentation.rocketmq.trace;

import brave.Span;
import brave.Tracing;
import brave.propagation.ThreadLocalSpan;
import brave.propagation.TraceContext;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.rocketmq.client.producer.SendStatus.SEND_OK;

/**
 * @program: brave-instrumentation-spring-rocketmq
 * @description:
 * @author: wangJun
 * @create: 2021-03-11 19:14
 **/
public class SendMessageTraceHook implements SendMessageHook {

    final ThreadLocalSpan threadLocalSpan = ThreadLocalSpan.CURRENT_TRACER;

    Logger log = LoggerFactory.getLogger(SendMessageTraceHook.class);

    private Tracing tracing;

    public void setTracing(Tracing tracing) {
        this.tracing = tracing;
    }

    @Override
    public String hookName() {
        return "SendMessageTraceHook";
    }

    @Override
    public void sendMessageBefore(SendMessageContext sendMessageContext) {

        TraceContext.Injector<Message> injector = tracing.propagation().injector(RocketMQPropagation.SETTER);

        Span span = threadLocalSpan.next();

        span.name(sendMessageContext.getMessage().getTopic() + ":" + sendMessageContext.getMessage().getProperty("TAGS"));

        String[] brokerAddr = sendMessageContext.getBrokerAddr().split(":");
        String brokerIp = brokerAddr[0];
        int brokerPort = Integer.parseInt(brokerAddr[1]);

        span.remoteServiceName(sendMessageContext.getMq().getBrokerName())
                .remoteIpAndPort(brokerIp, brokerPort);

        span.tag(RocketMqTag.ROCKET_MQ_TOPIC, sendMessageContext.getMessage().getTopic());
        span.tag(RocketMqTag.ROCKET_MQ_TAGS, sendMessageContext.getMessage().getTags());

        injector.inject(span.context(), sendMessageContext.getMessage());

        span.start();


    }

    @Override
    public void sendMessageAfter(SendMessageContext context) {
        try {
            Span span = threadLocalSpan.remove();

            //??????mq???????????????async?????????sendMessageHook?????????????????????
            //?????????????????????????????????????????????????????????callback??????
            //????????????????????????callback????????????span??????????????????
            if (span == null) {
                return;
            }


            if (context.getSendResult() == null || context.getSendResult().getSendStatus() == SEND_OK) {
                span.finish();
            } else {
                span.error(new RuntimeException(context.getSendResult().getSendStatus().name()))
                        .finish();
            }
        } catch (Exception e) {
            log.error("SendMessageTraceHook sendMessageAfter error", e);
        }



    }
}
