package com.junaldadlawan.event_ticketing_api.common.logging;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural guard for {@code logback-spring.xml}. Two things in it have
 * already gone wrong silently once and no behavioural test would notice:
 * <ul>
 *   <li>prod must declare Spring Boot's {@code StructuredLogEncoder} itself -
 *       with a custom logback file Boot no longer switches to JSON for you, so
 *       prod would quietly log plain text;</li>
 *   <li>the rolling FILE appender must never be attached in prod (container
 *       files vanish on restart; stdout to CloudWatch is the only sink).</li>
 * </ul>
 * Verified end to end by running the app with SPRING_PROFILES_ACTIVE=prod.
 */
class LogbackConfigurationTest {

    private static String xml;

    @BeforeAll
    static void load() throws IOException {
        xml = StreamUtils.copyToString(new ClassPathResource("logback-spring.xml").getInputStream(), StandardCharsets.UTF_8)
                .replaceAll("(?s)<!--.*?-->", "");
    }

    private static String profileBlock(String selector) {
        Matcher matcher = Pattern.compile("(?s)<springProfile name=\"" + Pattern.quote(selector) + "\">(.*?)</springProfile>").matcher(xml);
        assertThat(matcher.find()).as("<springProfile name=\"" + selector + "\"> block").isTrue();
        return matcher.group(1);
    }

    @Test
    void prodWritesStructuredJsonToStdout() {
        String prod = profileBlock("prod");
        assertThat(prod).contains("org.springframework.boot.logging.logback.StructuredLogEncoder");
        assertThat(prod).contains("<appender-ref ref=\"CONSOLE_JSON\"/>");
    }

    @Test
    void prodNeverAttachesTheFileAppender() {
        assertThat(profileBlock("prod")).doesNotContain("FILE").doesNotContain("RollingFileAppender");
    }

    @Test
    void nonProdFileAppender_writesJson_whileTheConsoleStaysReadableText() {
        String nonProd = profileBlock("!prod");
        // the rolling file is structured JSON, like prod ...
        assertThat(nonProd).contains("org.springframework.boot.logging.logback.StructuredLogEncoder")
                .contains("<format>${LOG_FILE_FORMAT}</format>");
        // ... and is not a plain-text pattern encoder
        assertThat(nonProd).doesNotContain("<pattern>");
        // the console keeps Boot's readable text appender
        assertThat(nonProd).contains("console-appender.xml").contains("<appender-ref ref=\"CONSOLE\"/>");
    }

    @Test
    void nonProd_auditAndApplicationLogsAreSeparateFiles() {
        String nonProd = profileBlock("!prod");

        Matcher auditLogger = Pattern.compile("(?s)<logger name=\"AUDIT\" additivity=\"false\">(.*?)</logger>").matcher(nonProd);
        assertThat(auditLogger.find()).as("<logger name=\"AUDIT\" additivity=\"false\">").isTrue();
        // audit lines go to the audit file (and the console) and NOT to the application file
        assertThat(auditLogger.group(1)).contains("<appender-ref ref=\"AUDIT_FILE\"/>")
                .contains("<appender-ref ref=\"CONSOLE\"/>")
                .doesNotContain("APP_FILE");

        Matcher root = Pattern.compile("(?s)<root level=\"INFO\">(.*?)</root>").matcher(nonProd);
        assertThat(root.find()).isTrue();
        // the root logger (everything else) writes the application file, never the audit file
        assertThat(root.group(1)).contains("<appender-ref ref=\"APP_FILE\"/>").doesNotContain("AUDIT_FILE");
    }

    @Test
    void nonProdFileAppender_isDatedFolder_withSizeCap_andNoFixedFileElement() {
        String nonProd = profileBlock("!prod");
        assertThat(nonProd).contains("SizeAndTimeBasedRollingPolicy")
                .contains("${LOG_DIR}/%d{yyyy-MM-dd}/application.%i.log")
                .contains("${LOG_DIR}/%d{yyyy-MM-dd}/audit.%i.log")
                .contains("<maxFileSize>${LOG_MAX_FILE_SIZE}</maxFileSize>")
                .contains("<totalSizeCap>${LOG_TOTAL_SIZE_CAP}</totalSizeCap>")
                // a fixed <file> would keep today's log outside today's folder
                .doesNotContain("<file>");
    }
}
