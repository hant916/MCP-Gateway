package com.mcpgateway.domain.ailuros;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Ailuros Control: Call Flag Entity
 * Manual flags for marking calls that require review or indicate issues
 */
@Entity
@Table(
    name = "ac_call_flag",
    indexes = {
        @Index(name = "idx_flag_call", columnList = "call_id"),
        @Index(name = "idx_flag_type", columnList = "flag_type, created_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcCallFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "call_id", nullable = false, foreignKey = @ForeignKey(name = "fk_flag_call"))
    private AcCall call;

    @Column(name = "flag_type", nullable = false, length = 32)
    private String flagType; // wrong, risky, review

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Flag type enumeration for type safety
     */
    public enum FlagType {
        WRONG("wrong", "Incorrect or unexpected output"),
        RISKY("risky", "Safety or policy concern"),
        REVIEW("review", "Requires human review");

        private final String value;
        private final String description;

        FlagType(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }

        public static FlagType fromValue(String value) {
            for (FlagType type : values()) {
                if (type.value.equalsIgnoreCase(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown flag type: " + value);
        }
    }
}
