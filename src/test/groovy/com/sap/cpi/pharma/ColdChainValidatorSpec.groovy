package com.sap.cpi.pharma

import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import spock.lang.Specification
import spock.lang.Unroll

class ColdChainValidatorSpec extends Specification {

    ColdChainValidator validator = new ColdChainValidator()
    Message message = Mock(Message)

    @Unroll
    def "GxP Cold-Chain Evaluation: #scenario (#temp #unit -> #expectedCelsius C => #expectedStatus)"() {
        given: "An incoming IoT cold-chain telemetry JSON payload"
        def inputJson = """
        {
            "batchId": "MRNA-CRISPR-2026-089",
            "sensorId": "CRYO-LOG-04",
            "temperature": ${temp},
            "unit": "${unit}"
        }
        """
        message.getBody(String.class) >> inputJson

        when: "The SAP CPI Groovy script processes the message"
        def result = validator.processData(message)

        then: "The message headers and GxP compliance status are accurately set"
        1 * message.setHeader("X-GxP-Batch-ID", "MRNA-CRISPR-2026-089")
        1 * message.setHeader("X-GxP-Normalized-Temp-C", expectedCelsius.toString())
        1 * message.setHeader("X-GxP-Release-Status", expectedStatus)
        1 * message.setHeader("CamelHttpResponseCode", expectedHttpCode)
        1 * message.setBody(_ as String) >> { args ->
            def parsed = new JsonSlurper().parseText(args[0])
            assert parsed.gxpAuditRecord.normalizedCelsius == expectedCelsius
            assert parsed.gxpAuditRecord.complianceStatus == expectedStatus
        }
        result == message

        where:
        scenario                       | temp   | unit | expectedCelsius | expectedStatus                          | expectedHttpCode
        "Optimal Celsius storage"      | -70.0  | "C"  | -70.0           | "RELEASED_FOR_PATIENT_ADMINISTRATION" | 200
        "Lower boundary Celsius"       | -80.0  | "C"  | -80.0           | "RELEASED_FOR_PATIENT_ADMINISTRATION" | 200
        "Upper boundary Celsius"       | -60.0  | "C"  | -60.0           | "RELEASED_FOR_PATIENT_ADMINISTRATION" | 200
        "US Fahrenheit cryo reading"   | -94.0  | "F"  | -70.0           | "RELEASED_FOR_PATIENT_ADMINISTRATION" | 200
        "Kelvin lab telemetry reading" | 203.15 | "K"  | -70.0           | "RELEASED_FOR_PATIENT_ADMINISTRATION" | 200
        "Critical warm excursion"      | -45.0  | "C"  | -45.0           | "QUARANTINE_THERMAL_EXCURSION"          | 422
        "Fahrenheit warm excursion"    | -40.0  | "F"  | -40.0           | "QUARANTINE_THERMAL_EXCURSION"          | 422
        "Over-freezing excursion"      | -95.0  | "C"  | -95.0           | "QUARANTINE_THERMAL_EXCURSION"          | 422
    }

    def "Should reject empty payloads to prevent silent GxP bypass"() {
        given: "An empty message body"
        message.getBody(String.class) >> "   "

        when: "The script runs"
        validator.processData(message)

        then: "An IllegalArgumentException is thrown immediately"
        thrown(IllegalArgumentException)
    }

    def "Should reject unsupported temperature units"() {
        given: "A payload with Rankine unit"
        message.getBody(String.class) >> '{"batchId":"B-01","temperature":365,"unit":"R"}'

        when: "The script runs"
        validator.processData(message)

        then: "An IllegalArgumentException is thrown"
        thrown(IllegalArgumentException)
    }
}
