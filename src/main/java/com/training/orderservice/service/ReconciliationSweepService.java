package com.training.orderservice.service;

/**
 * Scheduled reconciliation sweep (SDD Section 31): finds orders stuck in a transient
 * state past a threshold and retries the status commit, and retries previously-failed
 * stock-restore compensations.
 */
public interface ReconciliationSweepService {

    void runSweep();
}