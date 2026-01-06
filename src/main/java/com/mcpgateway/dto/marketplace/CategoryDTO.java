package com.mcpgateway.dto.marketplace;

import com.mcpgateway.domain.ToolCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * DTO for tool category
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDTO {

    private UUID id;
    private String name;
    private String slug;
    private String description;
    private String iconUrl;
    private UUID parentId;
    private Integer displayOrder;
    private List<CategoryDTO> children;

    /**
     * Convert from ToolCategory entity
     */
    public static CategoryDTO from(ToolCategory category) {
        return CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .iconUrl(category.getIconUrl())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .displayOrder(category.getDisplayOrder())
                .children(category.getChildren() != null ?
                        category.getChildren().stream()
                                .filter(c -> c.getIsActive())
                                .map(CategoryDTO::from)
                                .collect(Collectors.toList()) : null)
                .build();
    }
}
