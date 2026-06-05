package com.example.traning.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SetUpdateResponse {

	private Long id;
	private boolean isCompleted;
	private boolean isPR;
	/** PR更新時のメッセージ。isPR=false の場合は null */
	private String prMessage;
}
