/*
 * Copyright (c) 2015. Teradata Inc.
 */

package com.thinkbiganalytics.nifi.v2.spark;

import com.thinkbiganalytics.nifi.util.InputStreamReaderRunnable;

import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.annotation.behavior.EventDriven;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.LogLevel;
import org.apache.nifi.logging.ProcessorLog;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.spark.launcher.SparkLauncher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventDriven
@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
@Tags({"spark", "thinkbig"})
@CapabilityDescription("Execute a Spark job. "
)
public class ExecuteSparkJob extends AbstractProcessor {

    public static final String SPARK_NETWORK_TIMEOUT_CONFIG_NAME = "spark.network.timeout";

    // Relationships
    public static final Relationship REL_SUCCESS = new Relationship.Builder()
        .name("success")
        .description("Successful result.")
        .build();
    public static final Relationship REL_FAILURE = new Relationship.Builder()
        .name("failure")
        .description("Spark execution failed. Incoming FlowFile will be penalized and routed to this relationship")
        .build();
    private final Set<Relationship> relationships;

    public static final PropertyDescriptor APPLICATION_JAR = new PropertyDescriptor.Builder()
        .name("ApplicationJAR")
        .description("Path to the JAR file containing the Spark job application")
        .required(true)
        .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
        .expressionLanguageSupported(true)
        .build();

    public static final PropertyDescriptor MAIN_CLASS = new PropertyDescriptor.Builder()
        .name("MainClass")
        .description("Qualified classname of the Spark job application class")
        .required(true)
        .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
        .expressionLanguageSupported(true)
        .build();

    public static final PropertyDescriptor MAIN_ARGS = new PropertyDescriptor.Builder()
        .name("MainArgs")
        .description("Comma separated arguments to be passed into the main as args")
        .required(true)
        .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
        .expressionLanguageSupported(true)
        .build();

    public static final PropertyDescriptor SPARK_HOME = new PropertyDescriptor.Builder()
        .name("SparkHome")
        .description("Qualified classname of the Spark job application class")
        .required(true)
        .defaultValue("/usr/hdp/current/spark-client/")
        .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
        .expressionLanguageSupported(true)
        .build();

    public static final PropertyDescriptor SPARK_MASTER = new PropertyDescriptor.Builder()
        .name("SparkMaster")
        .description("The Spark master")
        .required(true)
        .defaultValue("local")
        .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
        .expressionLanguageSupported(true)
        .build();


    public static final PropertyDescriptor QUERY_TIMEOUT = new PropertyDescriptor.Builder()
        .name("Max Wait Time")
        .description("The maximum amount of time allowed for a running SQL select query "
                     + " , zero means there is no limit. Max time less than 1 second will be equal to zero.")
        .defaultValue("0 seconds")
        .required(true)
        .addValidator(StandardValidators.TIME_PERIOD_VALIDATOR)
        .sensitive(false)
        .build();

    public static final PropertyDescriptor DRIVER_MEMORY = new PropertyDescriptor.Builder()
        .name("Driver Memory")
        .description("How much RAM to allocate to the driver")
        .required(true)
        .defaultValue("512m")
        .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
        .expressionLanguageSupported(true)
        .build();
    public static final PropertyDescriptor EXECUTOR_MEMORY = new PropertyDescriptor.Builder()
        .name("Executor Memory")
        .description("How much RAM to allocate to the executor")
        .required(true)
        .defaultValue("512m")
        .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
        .expressionLanguageSupported(true)
        .build();
    public static final PropertyDescriptor NUMBER_EXECUTORS = new PropertyDescriptor.Builder()
        .name("Number of Executors")
        .description("The number of exectors to be used")
        .required(true)
        .defaultValue("1")
        .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
        .expressionLanguageSupported(true)
        .build();
    public static final PropertyDescriptor EXECUTOR_CORES = new PropertyDescriptor.Builder()
        .name("Executor Cores")
        .description("The number of executor cores to be used")
        .required(true)
        .defaultValue("1")
        .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
        .expressionLanguageSupported(true)
        .build();
    public static final PropertyDescriptor SPARK_APPLICATION_NAME = new PropertyDescriptor.Builder()
        .name("Spark Application Name")
        .description("The name of the spark application")
        .required(true)
        .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
        .expressionLanguageSupported(true)
        .build();
    public static final PropertyDescriptor NETWORK_TIMEOUT = new PropertyDescriptor.Builder()
        .name("Network Timeout")
        .description(
            "Default timeout for all network interactions. This config will be used in place of spark.core.connection.ack.wait.timeout, spark.akka.timeout, spark.storage.blockManagerSlaveTimeoutMs, spark.shuffle.io.connectionTimeout, spark.rpc.askTimeout or spark.rpc.lookupTimeout if they are not configured.")
        .required(true)
        .defaultValue("120s")
        .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
        .expressionLanguageSupported(true)
        .build();


    private final List<PropertyDescriptor> propDescriptors;

    public ExecuteSparkJob() {
        final Set<Relationship> r = new HashSet<>();
        r.add(REL_SUCCESS);
        r.add(REL_FAILURE);
        relationships = Collections.unmodifiableSet(r);

        final List<PropertyDescriptor> pds = new ArrayList<>();
        pds.add(APPLICATION_JAR);
        pds.add(MAIN_CLASS);
        pds.add(MAIN_ARGS);
        pds.add(SPARK_MASTER);
        pds.add(SPARK_HOME);
        pds.add(QUERY_TIMEOUT);
        pds.add(DRIVER_MEMORY);
        pds.add(EXECUTOR_MEMORY);
        pds.add(NUMBER_EXECUTORS);
        pds.add(SPARK_APPLICATION_NAME);
        pds.add(EXECUTOR_CORES);
        pds.add(NETWORK_TIMEOUT);
        propDescriptors = Collections.unmodifiableList(pds);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return relationships;
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return propDescriptors;
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
        final ProcessorLog logger = getLogger();
        FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }
        try {
              /* Configuration parameters for spark launcher */
            String appJar = context.getProperty(APPLICATION_JAR).evaluateAttributeExpressions(flowFile).getValue().trim();
            String mainClass = context.getProperty(MAIN_CLASS).evaluateAttributeExpressions(flowFile).getValue().trim();
            String sparkMaster = context.getProperty(SPARK_MASTER).evaluateAttributeExpressions(flowFile).getValue().trim();
            String appArgs = context.getProperty(MAIN_ARGS).evaluateAttributeExpressions(flowFile).getValue();
            String driverMemory = context.getProperty(DRIVER_MEMORY).evaluateAttributeExpressions(flowFile).getValue();
            String executorMemory = context.getProperty(EXECUTOR_MEMORY).evaluateAttributeExpressions(flowFile).getValue();
            String numberOfExecutors = context.getProperty(NUMBER_EXECUTORS).evaluateAttributeExpressions(flowFile).getValue();
            String sparkApplicationName = context.getProperty(SPARK_APPLICATION_NAME).evaluateAttributeExpressions(flowFile).getValue();
            String executorCores = context.getProperty(EXECUTOR_CORES).evaluateAttributeExpressions(flowFile).getValue();
            String networkTimeout = context.getProperty(NETWORK_TIMEOUT).evaluateAttributeExpressions(flowFile).getValue();
            String[] args = null;
            if (!StringUtils.isEmpty(appArgs)) {
                args = appArgs.split(",");
            }

            String sparkHome = context.getProperty(SPARK_HOME).getValue();

             /* Launch the spark job as a child process */
            SparkLauncher launcher = new SparkLauncher()
                .setAppResource(appJar)
                .setMainClass(mainClass)
                .setMaster(sparkMaster)
                .setConf(SparkLauncher.DRIVER_MEMORY, driverMemory)
                .setConf(SparkLauncher.EXECUTOR_CORES, numberOfExecutors)
                .setConf(SparkLauncher.EXECUTOR_MEMORY, executorMemory)
                .setConf(SparkLauncher.EXECUTOR_CORES, executorCores)
                .setConf(SPARK_NETWORK_TIMEOUT_CONFIG_NAME, networkTimeout)
                .setSparkHome(sparkHome)
                .setAppName(sparkApplicationName);
            if (args != null) {
                launcher.addAppArgs(args);
            }
            Process spark = launcher.launch();

            /* Read/clear the process input stream */
            InputStreamReaderRunnable inputStreamReaderRunnable = new InputStreamReaderRunnable(LogLevel.INFO, logger, spark.getInputStream());
            Thread inputThread = new Thread(inputStreamReaderRunnable, "stream input");
            inputThread.start();

             /* Read/clear the process error stream */
            InputStreamReaderRunnable errorStreamReaderRunnable = new InputStreamReaderRunnable(LogLevel.INFO, logger, spark.getErrorStream());
            Thread errorThread = new Thread(errorStreamReaderRunnable, "stream error");
            errorThread.start();

            logger.info("Waiting for Spark job to complete");

             /* Wait for job completion */
            int exitCode = spark.waitFor();
            if (exitCode != 0) {
                logger.info("*** Completed with failed status " + exitCode);
                session.transfer(flowFile, REL_FAILURE);
            } else {
                logger.info("*** Completed with status " + exitCode);
                session.transfer(flowFile, REL_SUCCESS);
            }
        } catch (final Exception e) {
            logger.error("Unable to execute Spark job", new Object[]{flowFile, e});
            session.transfer(flowFile, REL_FAILURE);
        }

    }


}