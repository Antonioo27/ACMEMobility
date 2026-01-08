# ACMEMobility - Microservices Architecture Project

Progetto per il corso di Architetture Software a Microservizi A.A. 2025/2026.

## Descrizione
Sistema SOA/Microservizi per la gestione di noleggio veicoli elettrici.

## Architettura
Il sistema è composto da:
- **Bank Service**: Implementato in Jolie (SOAP). Gestisce pagamenti e pre-autorizzazioni.
- **Fleet Management**: (In sviluppo) Microservizi per gestione veicoli.
- **Orchestrator**: Camunda BPMS.

## Struttura Repository
- `/services`: Codice sorgente dei servizi.
- `/bpmn`: Modelli di processo (Business Process).
- `/infrastructure`: File per Docker e Kubernetes.
- `/docs`: Documentazione e diagrammi di progetto.

## Quick Start (Bank Service)
1. Spostarsi in `services/bank-service-jolie`
2. Eseguire `jolie bankService.ol`
