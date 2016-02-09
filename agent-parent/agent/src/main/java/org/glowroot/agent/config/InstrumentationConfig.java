/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.agent.config;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.immutables.value.Value;

import org.glowroot.wire.api.model.AgentConfigOuterClass.AgentConfig;
import org.glowroot.wire.api.model.AgentConfigOuterClass.AgentConfig.CaptureKind;
import org.glowroot.wire.api.model.AgentConfigOuterClass.AgentConfig.MethodModifier;
import org.glowroot.wire.api.model.Proto.OptionalInt32;

@Value.Immutable
public abstract class InstrumentationConfig {

    @Value.Default
    @JsonInclude(value = Include.NON_EMPTY)
    public String className() {
        return "";
    }

    @Value.Default
    @JsonInclude(value = Include.NON_EMPTY)
    public String classAnnotation() {
        return "";
    }

    @Value.Default
    @JsonInclude(value = Include.NON_EMPTY)
    public String methodDeclaringClassName() {
        return "";
    }

    @Value.Default
    @JsonInclude(value = Include.NON_EMPTY)
    public String methodName() {
        return "";
    }

    @Value.Default
    @JsonInclude(value = Include.NON_EMPTY)
    public String methodAnnotation() {
        return "";
    }

    // empty methodParameterTypes means match no-arg methods only
    public abstract ImmutableList<String> methodParameterTypes();

    @Value.Default
    @JsonInclude(value = Include.NON_EMPTY)
    public String methodReturnType() {
        return "";
    }

    // currently unused, but will have a purpose someday, e.g. to capture all public methods
    @JsonInclude(value = Include.NON_EMPTY)
    public abstract ImmutableList<MethodModifier> methodModifiers();

    @Value.Default
    @JsonInclude(value = Include.NON_EMPTY)
    public String nestingGroup() {
        return "";
    }

    @Value.Default
    @JsonInclude(value = Include.NON_EMPTY)
    public int priority() {
        return 0;
    }

    public abstract CaptureKind captureKind();

    @Value.Default
    @JsonInclude(value = Include.NON_EMPTY)
    public String transactionType() {
        return "";
    }

    @Value.Default
    @JsonInclude(value = Include.NON_EMPTY)
    public String transactionNameTemplate() {
        return "";
    }

    @Value.Default
    @JsonInclude(value = Include.NON_EMPTY)
    public String transactionUserTemplate() {
        return "";
    }

    @JsonInclude(value = Include.NON_EMPTY)
    public abstract Map<String, String> transactionAttributeTemplates();

    // need to write zero since it is treated different from null
    @JsonInclude(value = Include.NON_NULL)
    public abstract @Nullable Integer transactionSlowThresholdMillis();

    @Value.Default
    @JsonInclude(value = Include.NON_EMPTY)
    public String traceEntryMessageTemplate() {
        return "";
    }

    // need to write zero since it is treated different from null
    @JsonInclude(value = Include.NON_NULL)
    public abstract @Nullable Integer traceEntryStackThresholdMillis();

    @Value.Default
    @JsonInclude(value = Include.NON_EMPTY)
    public boolean traceEntryCaptureSelfNested() {
        return false;
    }

    @Value.Default
    @JsonInclude(value = Include.NON_EMPTY)
    public String timerName() {
        return "";
    }

    // this is only for plugin authors (to be used in glowroot.plugin.json)
    @Value.Default
    @JsonInclude(value = Include.NON_EMPTY)
    public String enabledProperty() {
        return "";
    }

    // this is only for plugin authors (to be used in glowroot.plugin.json)
    @Value.Default
    @JsonInclude(value = Include.NON_EMPTY)
    public String traceEntryEnabledProperty() {
        return "";
    }

    @JsonIgnore
    @Value.Derived
    public boolean isTimerOrGreater() {
        return captureKind() == CaptureKind.TIMER || captureKind() == CaptureKind.TRACE_ENTRY
                || captureKind() == CaptureKind.TRANSACTION;
    }

    @JsonIgnore
    @Value.Derived
    public boolean isTraceEntryOrGreater() {
        return captureKind() == CaptureKind.TRACE_ENTRY || captureKind() == CaptureKind.TRANSACTION;
    }

    @JsonIgnore
    @Value.Derived
    public boolean isTransaction() {
        return captureKind() == CaptureKind.TRANSACTION;
    }

    @JsonIgnore
    @Value.Derived
    public ImmutableList<String> validationErrors() {
        List<String> errors = Lists.newArrayList();
        if (className().isEmpty() && classAnnotation().isEmpty()) {
            errors.add("className and classAnnotation are both empty");
        }
        if (methodName().isEmpty() && methodAnnotation().isEmpty()) {
            errors.add("methodName and methodAnnotation are both empty");
        }
        if (isTimerOrGreater() && timerName().isEmpty()) {
            errors.add("timerName is empty");
        }
        if (captureKind() == CaptureKind.TRACE_ENTRY && traceEntryMessageTemplate().isEmpty()) {
            errors.add("traceEntryMessageTemplate is empty");
        }
        if (isTransaction() && transactionType().isEmpty()) {
            errors.add("transactionType is empty");
        }
        if (isTransaction() && transactionNameTemplate().isEmpty()) {
            errors.add("transactionNameTemplate is empty");
        }
        if (!timerName().matches("[a-zA-Z0-9 ]*")) {
            errors.add("timerName contains invalid characters: " + timerName());
        }
        return ImmutableList.copyOf(errors);
    }

    public AgentConfig.InstrumentationConfig toProto() {
        AgentConfig.InstrumentationConfig.Builder builder =
                AgentConfig.InstrumentationConfig.newBuilder()
                        .setClassName(className())
                        .setClassAnnotation(classAnnotation())
                        .setMethodDeclaringClassName(methodDeclaringClassName())
                        .setMethodName(methodName())
                        .setMethodAnnotation(methodAnnotation())
                        .addAllMethodParameterType(methodParameterTypes())
                        .setMethodReturnType(methodReturnType())
                        .addAllMethodModifier(methodModifiers())
                        .setPriority(priority())
                        .setCaptureKind(captureKind())
                        .setTransactionType(transactionType())
                        .setTransactionNameTemplate(transactionNameTemplate())
                        .setTransactionUserTemplate(transactionUserTemplate())
                        .putAllTransactionAttributeTemplates(transactionAttributeTemplates());
        Integer transactionSlowThresholdMillis = transactionSlowThresholdMillis();
        if (transactionSlowThresholdMillis != null) {
            builder.setTransactionSlowThresholdMillis(
                    OptionalInt32.newBuilder().setValue(transactionSlowThresholdMillis));
        }
        builder.setTraceEntryMessageTemplate(traceEntryMessageTemplate());
        Integer traceEntryStackThresholdMillis = traceEntryStackThresholdMillis();
        if (traceEntryStackThresholdMillis != null) {
            builder.setTraceEntryStackThresholdMillis(
                    OptionalInt32.newBuilder().setValue(traceEntryStackThresholdMillis));
        }
        return builder.setTraceEntryCaptureSelfNested(traceEntryCaptureSelfNested())
                .setTimerName(timerName())
                .setEnabledProperty(enabledProperty())
                .setTraceEntryEnabledProperty(traceEntryEnabledProperty())
                .build();
    }

    public static InstrumentationConfig create(AgentConfig.InstrumentationConfig config) {
        ImmutableInstrumentationConfig.Builder builder = ImmutableInstrumentationConfig.builder()
                .className(config.getClassName())
                .classAnnotation(config.getClassAnnotation())
                .methodDeclaringClassName(config.getMethodDeclaringClassName())
                .methodName(config.getMethodName())
                .methodAnnotation(config.getMethodAnnotation())
                .addAllMethodParameterTypes(config.getMethodParameterTypeList())
                .methodReturnType(config.getMethodReturnType())
                .addAllMethodModifiers(config.getMethodModifierList())
                .priority(config.getPriority())
                .captureKind(config.getCaptureKind())
                .transactionType(config.getTransactionType())
                .transactionNameTemplate(config.getTransactionNameTemplate())
                .transactionUserTemplate(config.getTransactionUserTemplate())
                .putAllTransactionAttributeTemplates(config.getTransactionAttributeTemplates());
        if (config.hasTransactionSlowThresholdMillis()) {
            builder.transactionSlowThresholdMillis(
                    config.getTransactionSlowThresholdMillis().getValue());
        }
        builder.traceEntryMessageTemplate(config.getTraceEntryMessageTemplate());
        if (config.hasTraceEntryStackThresholdMillis()) {
            builder.traceEntryStackThresholdMillis(
                    config.getTraceEntryStackThresholdMillis().getValue());
        }
        return builder.traceEntryCaptureSelfNested(config.getTraceEntryCaptureSelfNested())
                .timerName(config.getTimerName())
                .enabledProperty(config.getEnabledProperty())
                .traceEntryEnabledProperty(config.getTraceEntryEnabledProperty())
                .build();
    }
}