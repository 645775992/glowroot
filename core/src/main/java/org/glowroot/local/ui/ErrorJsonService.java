/*
 * Copyright 2014-2015 the original author or authors.
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
package org.glowroot.local.ui;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;
import org.immutables.value.Json;
import org.immutables.value.Value;

import org.glowroot.collector.ErrorPoint;
import org.glowroot.collector.ErrorSummary;
import org.glowroot.collector.ErrorSummaryMarshaler;
import org.glowroot.collector.ImmutableErrorPoint;
import org.glowroot.common.Clock;
import org.glowroot.local.store.AggregateDao.ErrorSummarySortOrder;
import org.glowroot.local.store.ErrorMessageCount;
import org.glowroot.local.store.ErrorMessageCountMarshaler;
import org.glowroot.local.store.ErrorMessageQuery;
import org.glowroot.local.store.ImmutableErrorMessageQuery;
import org.glowroot.local.store.QueryResult;
import org.glowroot.local.store.TraceDao;
import org.glowroot.local.store.TraceErrorPoint;

@JsonService
class ErrorJsonService {

    private static final ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    }

    private final ErrorCommonService errorCommonService;
    private final TraceDao traceDao;
    private final long fixedAggregateIntervalMillis;
    private final DataSeriesHelper dataSeriesHelper;

    ErrorJsonService(ErrorCommonService errorCommonService, TraceDao traceDao, Clock clock,
            long fixedAggregateIntervalSeconds) {
        this.errorCommonService = errorCommonService;
        this.traceDao = traceDao;
        fixedAggregateIntervalMillis = fixedAggregateIntervalSeconds * 1000;
        dataSeriesHelper = new DataSeriesHelper(clock, fixedAggregateIntervalMillis);
    }

    @GET("/backend/error/messages")
    String getData(String queryString) throws Exception {
        ErrorMessageRequest request = QueryStrings.decode(queryString, ErrorMessageRequest.class);

        ImmutableErrorMessageQuery query = ImmutableErrorMessageQuery.builder()
                .transactionType(request.transactionType())
                .transactionName(request.transactionName())
                .from(request.from())
                .to(request.to())
                .addAllIncludes(request.includes())
                .addAllExcludes(request.excludes())
                .limit(request.errorMessageLimit())
                .build();
        QueryResult<ErrorMessageCount> queryResult = traceDao.readErrorMessageCounts(query);
        List<ErrorPoint> unfilteredErrorPoints = errorCommonService.readErrorPoints(
                query.transactionType(), query.transactionName(), query.from(), query.to());
        DataSeries dataSeries = new DataSeries(null);
        Map<Long, Long[]> dataSeriesExtra = Maps.newHashMap();
        if (query.includes().isEmpty() && query.excludes().isEmpty()) {
            populateDataSeries(query, unfilteredErrorPoints, dataSeries, dataSeriesExtra);
        } else {
            Map<Long, Long> transactionCountMap = Maps.newHashMap();
            for (ErrorPoint unfilteredErrorPoint : unfilteredErrorPoints) {
                transactionCountMap.put(unfilteredErrorPoint.captureTime(),
                        unfilteredErrorPoint.transactionCount());
            }
            ImmutableList<TraceErrorPoint> traceErrorPoints =
                    traceDao.readErrorPoints(query, fixedAggregateIntervalMillis);
            List<ErrorPoint> errorPoints = Lists.newArrayList();
            for (TraceErrorPoint traceErrorPoint : traceErrorPoints) {
                Long transactionCount = transactionCountMap.get(traceErrorPoint.captureTime());
                if (transactionCount != null) {
                    errorPoints.add(ImmutableErrorPoint.of(traceErrorPoint.captureTime(),
                            traceErrorPoint.errorCount(), transactionCount));
                }
            }
            populateDataSeries(query, errorPoints, dataSeries, dataSeriesExtra);
        }

        StringBuilder sb = new StringBuilder();
        JsonGenerator jg = mapper.getFactory().createGenerator(CharStreams.asWriter(sb));
        jg.writeStartObject();
        jg.writeObjectField("dataSeries", dataSeries);
        jg.writeObjectField("dataSeriesExtra", dataSeriesExtra);
        jg.writeFieldName("errorMessages");
        ErrorMessageCountMarshaler.instance().marshalIterable(jg, queryResult.records());
        jg.writeBooleanField("moreErrorMessagesAvailable", queryResult.moreAvailable());
        jg.writeEndObject();
        jg.close();
        return sb.toString();
    }

    @GET("/backend/error/summaries")
    String getSummaries(String queryString) throws Exception {
        ErrorSummaryRequest request = QueryStrings.decode(queryString, ErrorSummaryRequest.class);

        ErrorSummary overallSummary = errorCommonService.readOverallErrorSummary(
                request.transactionType(), request.from(), request.to());
        QueryResult<ErrorSummary> queryResult =
                errorCommonService.readTransactionErrorSummaries(request.transactionType(),
                        request.from(), request.to(), request.sortOrder(), request.limit());

        StringBuilder sb = new StringBuilder();
        JsonGenerator jg = mapper.getFactory().createGenerator(CharStreams.asWriter(sb));
        jg.writeStartObject();
        jg.writeFieldName("overall");
        ErrorSummaryMarshaler.instance().marshalInstance(jg, overallSummary);
        jg.writeFieldName("transactions");
        ErrorSummaryMarshaler.instance().marshalIterable(jg, queryResult.records());
        jg.writeBooleanField("moreAvailable", queryResult.moreAvailable());
        jg.writeEndObject();
        jg.close();
        return sb.toString();
    }

    @GET("/backend/error/tab-bar-data")
    String getTabBarData(String queryString) throws Exception {
        TabBarDataRequest request = QueryStrings.decode(queryString, TabBarDataRequest.class);

        String transactionName = request.transactionName();
        // requested from is in aggregate terms, so need to subtract aggregate interval to get
        // real timing interval which is needed for trace queries
        long from = request.from() - fixedAggregateIntervalMillis;

        long traceCount;
        if (transactionName == null) {
            traceCount = traceDao.readOverallErrorCount(request.transactionType(), from,
                    request.to());
        } else {
            traceCount = traceDao.readTransactionErrorCount(request.transactionType(),
                    transactionName, from, request.to());
        }

        StringBuilder sb = new StringBuilder();
        JsonGenerator jg = mapper.getFactory().createGenerator(CharStreams.asWriter(sb));
        jg.writeStartObject();
        jg.writeNumberField("traceCount", traceCount);
        jg.writeEndObject();
        jg.close();
        return sb.toString();
    }
    private void populateDataSeries(ErrorMessageQuery request, List<ErrorPoint> errorPoints,
            DataSeries dataSeries, Map<Long, Long[]> dataSeriesExtra) {
        ErrorPoint lastErrorPoint = null;
        for (ErrorPoint errorPoint : errorPoints) {
            if (lastErrorPoint == null) {
                // first aggregate
                dataSeriesHelper.addInitialUpslope(request.from(), errorPoint.captureTime(),
                        dataSeries);
            } else {
                dataSeriesHelper.addGapIfNeeded(lastErrorPoint.captureTime(),
                        errorPoint.captureTime(), dataSeries);
            }
            lastErrorPoint = errorPoint;
            long transactionCount = errorPoint.transactionCount();
            dataSeries.add(errorPoint.captureTime(),
                    100 * errorPoint.errorCount() / (double) transactionCount);
            dataSeriesExtra.put(errorPoint.captureTime(),
                    new Long[] {errorPoint.errorCount(), transactionCount});
        }
        if (lastErrorPoint != null) {
            dataSeriesHelper.addFinalDownslope(request.to(), dataSeries,
                    lastErrorPoint.captureTime());
        }
    }

    @Value.Immutable
    @Json.Marshaled
    abstract static class ErrorSummaryRequest {
        abstract long from();
        abstract long to();
        abstract String transactionType();
        abstract ErrorSummarySortOrder sortOrder();
        abstract int limit();
    }

    @Value.Immutable
    @Json.Marshaled
    abstract static class TabBarDataRequest {
        abstract long from();
        abstract long to();
        abstract String transactionType();
        abstract @Nullable String transactionName();
    }

    @Value.Immutable
    @Json.Marshaled
    abstract static class ErrorMessageRequest {
        abstract long from();
        abstract long to();
        abstract String transactionType();
        abstract @Nullable String transactionName();
        public abstract List<String> includes();
        public abstract List<String> excludes();
        abstract int errorMessageLimit();
    }
}
