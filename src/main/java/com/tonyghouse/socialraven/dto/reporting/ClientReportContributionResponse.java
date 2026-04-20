package com.tonyghouse.socialraven.dto.reporting;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientReportContributionResponse {
    private String dimension;
    private String dimensionLabel;
    private List<ClientReportContributionRowResponse> rows = new ArrayList<>();
}
