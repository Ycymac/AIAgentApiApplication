package com.ycy.aiapplication.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "鍒犻櫎闈㈣瘯璁板綍璇锋眰鍙傛暟")
public class DeleteInterviewRecordReqDTO {

    @Schema(description = "闈㈣瘯璁板綍鏁版嵁搴搃d", example = "2029419678962561026")
    private String id;
}
