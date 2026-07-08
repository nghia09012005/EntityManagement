package com.viettelDigitalTalent.EntitiyManagement.graph.service;

import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.AuthenticationEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.NetworkEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.ProcessEvent;

import java.util.Map;

public final class EventFieldAccessor {

    private EventFieldAccessor() {}

    public static String getValue(BaseEvent event, String fieldName) {
        if (event == null || fieldName == null || fieldName.isBlank()) return null;

        Map<String, Object> raw = event.getRawData();
        if (raw != null && raw.containsKey(fieldName)) {
            Object v = raw.get(fieldName);
            return v != null ? String.valueOf(v) : null;
        }

        if (event instanceof AuthenticationEvent ae) {
            return switch (fieldName) {
                case "username"    -> ae.getUsername();
                case "ipAddress"   -> ae.getIpAddress();
                case "workstation" -> ae.getWorkstation();
                default -> null;
            };
        }

        if (event instanceof ProcessEvent pe) {
            return switch (fieldName) {
                case "processName" -> pe.getProcessName();
                case "processPath" -> pe.getProcessPath();
                case "fileHash"    -> pe.getFileHash();
                case "commandLine" -> pe.getCommandLine();
                default -> null;
            };
        }

        if (event instanceof NetworkEvent ne) {
            return switch (fieldName) {
                case "srcIp"     -> ne.getSrcIp();
                case "dstIp"     -> ne.getDstIp();
                case "dstDomain" -> ne.getDstDomain();
                default -> null;
            };
        }

        if (event instanceof AlertEvent alert) {
            return switch (fieldName) {
                case "alertName"              -> alert.getAlertName();
                case "severity"               -> alert.getSeverity();
                case "description"            -> alert.getDescription();
                case "targetIp"               -> alert.getTargetIp();
                case "targetUser"             -> alert.getTargetUser();
                case "targetHost"             -> alert.getTargetHost();
                case "targetDomain"           -> alert.getTargetDomain();
                case "targetFileHash"         -> alert.getTargetFileHash();
                case "targetProcess"          -> alert.getTargetProcess();
                case "targetUrl"              -> alert.getTargetUrl();
                case "targetCloudResourceId"  -> alert.getTargetCloudResourceId();
                case "targetEmail"            -> alert.getTargetEmail();
                case "targetCve"              -> alert.getTargetCve();
                case "sourceIp"               -> alert.getSourceIp();
                case "sourceHost"             -> alert.getSourceHost();
                case "sourceDomain"           -> alert.getSourceDomain();
                default -> null;
            };
        }

        return null;
    }
}
