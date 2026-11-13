# ADR-026: Automated Spock TDD & GitOps Quality Gates for GxP SAP CPI Groovy Scripts

## Status
Accepted & Enforced via GitHub Actions

## Context
In clinical and commercial distribution of CRISPR and mRNA genetic therapies, ultra-cold chain telemetry (-80°C to -60°C) is processed in real time by SAP Cloud Integration (CPI) Groovy scripts. Deploying unverified Groovy logic directly onto SAP BTP tenants via manual UI clicks violates EU Annex 11 and FDA 21 CFR Part 11 computer system validation (CSV) principles and risks silent thermal excursions worth millions of dollars.

## Decision
1. **Isolated Compile-Time Interface Stubbing:** Mirror `com.sap.gateway.ip.core.customdev.util.Message` in a dedicated `compileStub` Gradle source set so production Groovy scripts remain pure while enabling zero-dependency compilation.
2. **Spock Framework Mocking & Data-Driven Testing:** Mandate 100% test coverage of temperature unit normalization (`C`, `F`, `K`), boundary conditions, and exception paths using Spock `Mock(Message)` and `@Unroll` matrices.
3. **GitHub Actions + SAP Project Piper Quality Gate:** Block any deployment (`cpiArtifactDeploy`) to SAP BTP unless the automated `gradle test` pipeline succeeds on push.

## Consequences
- **Zero Production Regressions:** Faulty unit conversions or broken Camel headers fail the CI build in under 30 seconds before touching SAP BTP.
- **Audit-Ready Traceability:** Every commit generates an immutable Spock HTML verification artifact linked to the Git SHA.
