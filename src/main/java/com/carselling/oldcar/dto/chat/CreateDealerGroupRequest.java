package com.carselling.oldcar.dto.chat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * Request DTO for creating dealer-only groups
 */
public class CreateDealerGroupRequest {

    @NotBlank(message = "Group name is required")
    @Size(min = 1, max = 100, message = "Group name must be between 1 and 100 characters")
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @Size(max = 50, message = "Can invite maximum 50 dealers at once")
    private List<Long> dealerIds;

    private String groupImageUrl;

    private Boolean isPrivate = false;

    // Constructors
    public CreateDealerGroupRequest() {}

    public CreateDealerGroupRequest(String name, String description) {
        this.name = name;
        this.description = description;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Long> getDealerIds() {
        return dealerIds;
    }

    public void setDealerIds(List<Long> dealerIds) {
        this.dealerIds = dealerIds;
    }

    public String getGroupImageUrl() {
        return groupImageUrl;
    }

    public void setGroupImageUrl(String groupImageUrl) {
        this.groupImageUrl = groupImageUrl;
    }

    public Boolean getIsPrivate() {
        return isPrivate;
    }

    public void setIsPrivate(Boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    @Override
    public String toString() {
        return "CreateDealerGroupRequest{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", dealerIds=" + dealerIds +
                ", groupImageUrl='" + groupImageUrl + '\'' +
                ", isPrivate=" + isPrivate +
                '}';
    }
}
