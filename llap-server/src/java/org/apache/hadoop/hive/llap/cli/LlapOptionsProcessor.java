/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.llap.cli;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.annotation.Nonnull;

import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hadoop.util.StringUtils;

public class LlapOptionsProcessor {

  public static final String OPTION_SLIDER_KEYTAB_DIR = "slider-keytab-dir";
  public static final String OPTION_SLIDER_KEYTAB = "slider-keytab";
  public static final String OPTION_SLIDER_PRINCIPAL = "slider-principal";
  public static final String OPTION_SLIDER_DEFAULT_KEYTAB = "slider-default-keytab";


  public class LlapOptions {
    private final int instances;
    private final String directory;
    private final String name;
    private final int executors;
    private final long cache;
    private final long size;
    private final long xmx;
    private final String jars;
    private final boolean isHbase;
    private final Properties conf;

    public LlapOptions(String name, int instances, String directory, int executors, long cache,
        long size, long xmx, String jars, boolean isHbase, @Nonnull Properties hiveconf)
            throws ParseException {
      if (instances <= 0) {
        throw new ParseException("Invalid configuration: " + instances
            + " (should be greater than 0)");
      }
      this.instances = instances;
      this.directory = directory;
      this.name = name;
      this.executors = executors;
      this.cache = cache;
      this.size = size;
      this.xmx = xmx;
      this.jars = jars;
      this.isHbase = isHbase;
      this.conf = hiveconf;
    }

    public String getName() {
      return name;
    }

    public int getInstances() {
      return instances;
    }

    public String getDirectory() {
      return directory;
    }

    public int getExecutors() {
      return executors;
    }

    public long getCache() {
      return cache;
    }

    public long getSize() {
      return size;
    }

    public long getXmx() {
      return xmx;
    }

    public String getAuxJars() {
      return jars;
    }

    public boolean getIsHBase() {
      return isHbase;
    }

    public Properties getConfig() {
      return conf;
    }
  }

  protected static final Logger l4j = LoggerFactory.getLogger(LlapOptionsProcessor.class.getName());
  private final Options options = new Options();
  Map<String, String> hiveVariables = new HashMap<String, String>();
  private org.apache.commons.cli.CommandLine commandLine;

  @SuppressWarnings("static-access")
  public LlapOptionsProcessor() {

    // set the number of instances on which llap should run
    options.addOption(OptionBuilder.hasArg().withArgName("instances").withLongOpt("instances")
        .withDescription("Specify the number of instances to run this on").create('i'));

    options.addOption(OptionBuilder.hasArg().withArgName("name").withLongOpt("name")
        .withDescription("Cluster name for YARN registry").create('n'));

    options.addOption(OptionBuilder.hasArg().withArgName("directory").withLongOpt("directory")
        .withDescription("Temp directory for jars etc.").create('d'));

    options.addOption(OptionBuilder.hasArg().withArgName("args").withLongOpt("args")
        .withDescription("java arguments to the llap instance").create('a'));

    options.addOption(OptionBuilder.hasArg().withArgName("loglevel").withLongOpt("loglevel")
        .withDescription("log levels for the llap instance").create('l'));

    options.addOption(OptionBuilder.hasArg().withArgName("chaosmonkey").withLongOpt("chaosmonkey")
        .withDescription("chaosmonkey interval").create('m'));

    options.addOption(OptionBuilder.hasArg().withArgName("executors").withLongOpt("executors")
        .withDescription("executor per instance").create('e'));

    options.addOption(OptionBuilder.hasArg(false).withArgName(OPTION_SLIDER_DEFAULT_KEYTAB).withLongOpt(OPTION_SLIDER_DEFAULT_KEYTAB)
        .withDescription("try to set default settings for Slider AM keytab; mostly for dev testing").create());

    options.addOption(OptionBuilder.hasArg().withArgName(OPTION_SLIDER_KEYTAB_DIR).withLongOpt(OPTION_SLIDER_KEYTAB_DIR)
        .withDescription("Slider AM keytab directory on HDFS (where the headless user keytab is stored by Slider keytab installation, e.g. .slider/keytabs/llap)").create());

    options.addOption(OptionBuilder.hasArg().withArgName(OPTION_SLIDER_KEYTAB).withLongOpt(OPTION_SLIDER_KEYTAB)
        .withDescription("Slider AM keytab file name inside " + OPTION_SLIDER_KEYTAB_DIR).create());

    options.addOption(OptionBuilder.hasArg().withArgName(OPTION_SLIDER_PRINCIPAL).withLongOpt(OPTION_SLIDER_PRINCIPAL)
        .withDescription("Slider AM principal; should be the user running the cluster, e.g. hive@EXAMPLE.COM").create());

    options.addOption(OptionBuilder.hasArg().withArgName("cache").withLongOpt("cache")
        .withDescription("cache size per instance").create('c'));

    options.addOption(OptionBuilder.hasArg().withArgName("size").withLongOpt("size")
        .withDescription("container size per instance").create('s'));

    options.addOption(OptionBuilder.hasArg().withArgName("xmx").withLongOpt("xmx")
        .withDescription("working memory size").create('w'));

    options.addOption(OptionBuilder.hasArg().withArgName("auxjars").withLongOpt("auxjars")
        .withDescription("additional jars to package (by default, JSON SerDe jar is packaged"
            + " if available)").create('j'));

    options.addOption(OptionBuilder.hasArg().withArgName("auxhbase").withLongOpt("auxhbase")
        .withDescription("whether to package the HBase jars (true by default)").create('h'));

    // -hiveconf x=y
    options.addOption(OptionBuilder.withValueSeparator().hasArgs(2).withArgName("property=value")
        .withLongOpt("hiveconf").withDescription("Use value for given property").create());

    // [-H|--help]
    options.addOption(new Option("H", "help", false, "Print help information"));
  }

  private static long parseSuffixed(String value) {
    return StringUtils.TraditionalBinaryPrefix.string2long(value);
  }

  public LlapOptions processOptions(String argv[]) throws ParseException {
    commandLine = new GnuParser().parse(options, argv);
    if (commandLine.hasOption('H') || false == commandLine.hasOption("instances")) {
      // needs at least --instances
      printUsage();
      return null;
    }

    int instances = Integer.parseInt(commandLine.getOptionValue("instances"));
    String directory = commandLine.getOptionValue("directory");
    String jars = commandLine.getOptionValue("auxjars");

    String name = commandLine.getOptionValue("name", null);

    final int executors = Integer.parseInt(commandLine.getOptionValue("executors", "-1"));
    final long cache = parseSuffixed(commandLine.getOptionValue("cache", "-1"));
    final long size = parseSuffixed(commandLine.getOptionValue("size", "-1"));
    final long xmx = parseSuffixed(commandLine.getOptionValue("xmx", "-1"));
    final boolean isHbase = Boolean.parseBoolean(commandLine.getOptionValue("auxhbase", "true"));

    final Properties hiveconf;

    if (commandLine.hasOption("hiveconf")) {
      hiveconf = commandLine.getOptionProperties("hiveconf");
    } else {
      hiveconf = new Properties();
    }

    // loglevel, chaosmonkey & args are parsed by the python processor

    return new LlapOptions(
        name, instances, directory, executors, cache, size, xmx, jars, isHbase, hiveconf);

  }

  private void printUsage() {
    new HelpFormatter().printHelp("llap", options);
  }
}