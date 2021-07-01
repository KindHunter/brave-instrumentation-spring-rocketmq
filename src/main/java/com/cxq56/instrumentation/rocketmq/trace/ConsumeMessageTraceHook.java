package com.cxq56.instrumentation.rocketmq.trace;

import brave.Span;
import brave.Tracing;
import brave.propagation.ThreadLocalSpan;
import brave.propagation.TraceContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeReturnType;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * @program: brave-instrumentation-spring-rocketmq
 * @description:
 * @author: wangJun
 * @create: 2021-03-11 20:15
 **/
public class ConsumeMessageTraceHook implements ConsumeMessageHook {

    final ThreadLocalSpan threadLocalSpan = ThreadLocalSpan.CURRENT_TRACER;

    Logger log = LoggerFactory.getLogger(ConsumeMessageTraceHook.class);

    private Tracing tracing;

    public void setTracing(Tracing tracing) {
        this.tracing = tracing;
    }

    @Override
    public String hookName() {
        return "ConsumeMessageTraceHook";
    }

    @Override
    public void consumeMessageBefore(ConsumeMessageContext context) {

        TraceContext.Extractor<Message> extractor = tracing.propagation().extractor(RocketMQPropagation.GETTER);

        List<MessageExt> msgList = context.getMsgList();

        if (msgList.size() == 0) {
            return;
        }

        MessageExt messageExt = msgList.get(0);

        //默认情况下msgList的size都是1
        Span span = threadLocalSpan.next(extractor.extract(messageExt));
        span.name(messageExt.getTopic() + ":" + messageExt.getProperty("TAGS"));

        span.start();
    }

    @Override
    public void consumeMessageAfter(ConsumeMessageContext context) {

        Span span = null;

        RuntimeException error = null;

        try {
            span = threadLocalSpan.remove();

            context.getProps().forEach(span::tag);

            String consumeResult = context.getProps().get(MixAll.CONSUME_CONTEXT_TYPE);

            if (! Objects.equals(consumeResult, String.valueOf(ConsumeReturnType.SUCCESS))
                    && ! Objects.equals(consumeResult, String.valueOf(ConsumeReturnType.RETURNNULL))) {

                error = new RuntimeException(consumeResult);
            }

        } catch (Exception e) {
            log.error("consumeMessageAfter error!", e);
        } finally {

            if (span != null) {

                if (Objects.isNull(error)) {

                    span.finish();

                } else {
                    span.error(error).finish();
                }
            }
        }
    }
}
