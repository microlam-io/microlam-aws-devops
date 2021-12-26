package io.microlam.aws.devops;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.microlam.aws.auth.AwsProfileRegionClientConfigurator;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest;
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest.Builder;
import software.amazon.awssdk.services.lambda.model.ListFunctionsResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeResponse;


public class LambdaUtils {

	private static Logger LOGGER = LoggerFactory.getLogger(LambdaUtils.class);

	public static LambdaClient createLambdaClient() {
		return AwsProfileRegionClientConfigurator.getInstance().configure(LambdaClient.builder()).build();
	}
	
	public static void updateLambdaCode(String[] allLambdas, String bucket, String s3Key) {
		LambdaClient lambdaClient = LambdaUtils.createLambdaClient();
		List<String> discoveredAllLambda = new ArrayList<>();
		String nextMarker = null;
		ListFunctionsResponse listFunctionsResponse;		
		do {
			Builder builder = ListFunctionsRequest.builder();
			builder.maxItems(1000);
			if (nextMarker != null) {
				builder.marker(nextMarker);
			}
			listFunctionsResponse = lambdaClient.listFunctions((ListFunctionsRequest) builder.build());
			List<FunctionConfiguration> list = listFunctionsResponse.functions();
			for(FunctionConfiguration functionConfiguration: list) {
				discoveredAllLambda.add(functionConfiguration.functionName());
				LOGGER.info("Discovered Lambda : {}", functionConfiguration.functionName());
			}
			nextMarker = listFunctionsResponse.nextMarker();
		}
		while(nextMarker != null);
		LOGGER.info("Discovered total: {}", discoveredAllLambda.size());

		for(String functionName : allLambdas) { 
			LOGGER.info("Updating code for lambda: {}...", functionName);
			UpdateFunctionCodeResponse resp = lambdaClient.updateFunctionCode((UpdateFunctionCodeRequest)UpdateFunctionCodeRequest.builder().s3Bucket(bucket).s3Key(s3Key).functionName(functionName).build());
			if (resp.sdkHttpResponse().isSuccessful()) {
				LOGGER.info("Code for Lambda: {} updated with version = {} and revision = {}", functionName, resp.version(), resp.revisionId());
			}
			else {
				LOGGER.warn("Cannot update Code for Lambda {}", functionName);
			}
		}
		
	}

}