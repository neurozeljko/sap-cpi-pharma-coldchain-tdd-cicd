import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def Message processData(Message message) {
    def body = message.getBody(String.class)
    if (!body || body.trim().isEmpty()) {
        throw new IllegalArgumentException("GxP Critical Error: Empty telemetry payload received.")
    }

    def slurper = new JsonSlurper()
    def payload = slurper.parseText(body)

    if (payload.temperature == null || !payload.unit || !payload.batchId) {
        throw new IllegalArgumentException("GxP Validation Error: Missing mandatory fields (batchId, temperature, unit).")
    }

    double rawTemp = payload.temperature as double
    String unit = payload.unit.toString().trim().toUpperCase()
    double tempCelsius

    switch (unit) {
        case "C":
            tempCelsius = rawTemp
            break
        case "F":
            tempCelsius = (rawTemp - 32.0d) * (5.0d / 9.0d)
            break
        case "K":
            tempCelsius = rawTemp - 273.15d
            break
        default:
            throw new IllegalArgumentException("GxP Unit Error: Unsupported temperature scale '${unit}'. Allowed: C, F, K.")
    }

    tempCelsius = Math.round(tempCelsius * 100.0d) / 100.0d

    // Ultra-Cold Chain Threshold for mRNA / CRISPR biologics: -80.0°C to -60.0°C
    boolean isCompliant = (tempCelsius >= -80.0d && tempCelsius <= -60.0d)
    String releaseStatus = isCompliant ? "RELEASED_FOR_PATIENT_ADMINISTRATION" : "QUARANTINE_THERMAL_EXCURSION"
    int httpStatus = isCompliant ? 200 : 422

    message.setHeader("X-GxP-Batch-ID", payload.batchId.toString())
    message.setHeader("X-GxP-Normalized-Temp-C", tempCelsius.toString())
    message.setHeader("X-GxP-Release-Status", releaseStatus)
    message.setHeader("CamelHttpResponseCode", httpStatus)

    def responseMap = [
        gxpAuditRecord: [
            batchId: payload.batchId,
            sensorId: payload.sensorId ?: "UNKNOWN_SENSOR",
            originalReading: "${rawTemp} ${unit}",
            normalizedCelsius: tempCelsius,
            allowedRangeCelsius: "-80.0 to -60.0",
            complianceStatus: releaseStatus,
            regulatoryFramework: "EU Annex 11 / FDA 21 CFR Part 11"
        ]
    ]

    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(responseMap)))
    return message
}