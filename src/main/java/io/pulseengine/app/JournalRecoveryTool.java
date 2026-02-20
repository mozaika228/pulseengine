package io.pulseengine.app;

import io.pulseengine.persistence.JournalScanner;

import java.nio.file.Path;

public final class JournalRecoveryTool {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("usage: JournalRecoveryTool <verify|repair> <journalPath>");
            return;
        }

        String cmd = args[0];
        Path path = Path.of(args[1]);

        JournalScanner.ScanResult result;
        if ("repair".equalsIgnoreCase(cmd)) {
            result = JournalScanner.repair(path);
            System.out.println("repair_completed=true");
        } else {
            result = JournalScanner.scan(path);
        }

        System.out.println("valid_records=" + result.validRecords());
        System.out.println("valid_bytes=" + result.validBytes());
        System.out.println("corrupted=" + result.corrupted());
        System.out.println("corrupted_record_index=" + result.corruptedRecordIndex());
        System.out.println("reason=" + result.reason());
    }
}
