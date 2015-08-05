/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.plugins.quality

import org.gradle.api.GradleException
import org.gradle.api.Incubating
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.IsolatedAntBuilder
import org.gradle.api.plugins.quality.internal.CheckstyleReportsImpl
import org.gradle.api.reporting.Reporting
import org.gradle.api.resources.TextResource
import org.gradle.api.tasks.*
import org.gradle.internal.reflect.Instantiator
import org.gradle.logging.ConsoleRenderer

import javax.inject.Inject

/**
 * Runs Checkstyle against some source files.
 */
class Checkstyle extends SourceTask implements VerificationTask, Reporting<CheckstyleReports> {
    /**
     * The class path containing the Checkstyle library to be used.
     */
    @InputFiles
    FileCollection checkstyleClasspath

    /**
     * The class path containing the compiled classes for the source files to be analyzed.
     */
    @InputFiles
    FileCollection classpath

    /**
     * The Checkstyle configuration to use. Replaces the {@code configFile} property.
     *
     * @since 2.2
     */
    @Incubating
    @Nested
    TextResource config

    /**
     * The Checkstyle configuration file to use.
     */
    File getConfigFile() {
        getConfig()?.asFile()
    }

    /**
     * The Checkstyle configuration file to use.
     */
    void setConfigFile(File configFile) {
        setConfig(project.resources.text.fromFile(configFile))
    }

    /**
     * The properties available for use in the configuration file. These are substituted into the configuration
     * file.
     */
    @Input
    @Optional
    Map<String, Object> configProperties = [:]

    @Nested
    private final CheckstyleReportsImpl reports

    Checkstyle() {
        reports = instantiator.newInstance(CheckstyleReportsImpl, this)
    }

    @Inject
    Instantiator getInstantiator() {
        throw new UnsupportedOperationException();
    }

    @Inject
    IsolatedAntBuilder getAntBuilder() {
        throw new UnsupportedOperationException();
    }

    /**
     * The reports to be generated by this task.
     *
     * @return The reports container
     */
    CheckstyleReports getReports() {
        reports
    }

    /**
     * Configures the reports to be generated by this task.
     *
     * The contained reports can be configured by name and closures. Example:
     *
     * <pre>
     * checkstyleTask {
     *   reports {
     *     xml {
     *       destination "build/codenarc.xml"
     *     }
     *   }
     * }
     * </pre>
     *
     * @param closure The configuration
     * @return The reports container
     */
    CheckstyleReports reports(Closure closure) {
        reports.configure(closure)
    }

    /**
     * Whether or not this task will ignore failures and continue running the build.
     */
    boolean ignoreFailures

    /**
     * Whether or not rule violations are to be displayed on the console.
     */
    boolean showViolations = true

    /**
     * Class name for ant to call. In 6.8, the task was renamed from CheckStyleTask to CheckstyleAntTask
     *
     * For more details check <a href="http://checkstyle.sourceforge.net/releasenotes.html#Release_6.8">the release notes</a>
     */
    String antClassName

    @TaskAction
    public void run() {
        def propertyName = "org.gradle.checkstyle.violations"
        antBuilder.withClasspath(getCheckstyleClasspath()).execute {
            ant.taskdef(name: 'checkstyle', classname: antClassName)

            ant.checkstyle(config: getConfig().asFile(), failOnViolation: false, failureProperty: propertyName) {
                getSource().addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
                getClasspath().addToAntBuilder(ant, 'classpath')
                if (showViolations) {
                    formatter(type: 'plain', useFile: false)
                }
                if (reports.xml.enabled) {
                    formatter(type: 'xml', toFile: reports.xml.destination)
                }

                getConfigProperties().each { key, value ->
                    property(key: key, value: value.toString())
                }
            }

            if (ant.project.properties[propertyName]) {
                def message = "Checkstyle rule violations were found."
                def report = reports.firstEnabled
                if (report) {
                    def reportUrl = new ConsoleRenderer().asClickableFileUrl(report.destination)
                    message += " See the report at: $reportUrl"
                }
                if (getIgnoreFailures()) {
                    logger.warn(message)
                } else {
                    throw new GradleException(message)
                }
            }
        }
    }
}
