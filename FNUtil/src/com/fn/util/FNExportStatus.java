package com.fn.util;

public class FNExportStatus {
	public static final Integer Export_COMPLETED=0;
	public static final Integer EXPORT_CONTENT_ERROR=1;
	public static final Integer EXPORT_ANNOTATION_ERROR=2;
	public static final Integer INSERT_CONTENT_ERROR=4;
	public static final Integer INSERT_ANNOATION_ERROR=8;
	public static final Integer NO_CONTENT_FILE=16;
	public static final Integer FAIL_CREATE_PROPTERY_FILE=32;
	public static final Integer CONTENT_FCA_FILE_DOES_NOT_EXIST=64;
	public static final Integer OTHER_ERROR=1024;
}
