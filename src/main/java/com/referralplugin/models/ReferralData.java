package com.referralplugin.models;

import java.util.UUID;

public class ReferralData {

    private UUID uuid;
    private String username;
    private String referralCode;
    private UUID referredByUUID;
    private int totalReferrals;
    private long joinTimestamp;
    private long referralCodeUsedAt;

    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }

    public UUID getReferredByUUID() { return referredByUUID; }
    public void setReferredByUUID(UUID referredByUUID) { this.referredByUUID = referredByUUID; }

    public int getTotalReferrals() { return totalReferrals; }
    public void setTotalReferrals(int totalReferrals) { this.totalReferrals = totalReferrals; }

    public long getJoinTimestamp() { return joinTimestamp; }
    public void setJoinTimestamp(long joinTimestamp) { this.joinTimestamp = joinTimestamp; }

    public long getReferralCodeUsedAt() { return referralCodeUsedAt; }
    public void setReferralCodeUsedAt(long referralCodeUsedAt) { this.referralCodeUsedAt = referralCodeUsedAt; }

    public boolean hasUsedCode() { return referredByUUID != null; }
}
