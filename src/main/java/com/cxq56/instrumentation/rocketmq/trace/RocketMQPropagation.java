package com.cxq56.instrumentation.rocketmq.trace;

import brave.propagation.Propagation;
import org.apache.rocketmq.common.message.Message;

/**
 * @program: brave-instrumentation-spring-rocketmq
 * @description:
 * @author: wangJun
 * @create: 2021-03-12 16:02
 **/
public class RocketMQPropagation {

    static final Propagation.Setter<Message, String> SETTER = Message::putUserProperty;

    static final Propagation.Getter<Message, String> GETTER = Message::getUserProperty;

}
