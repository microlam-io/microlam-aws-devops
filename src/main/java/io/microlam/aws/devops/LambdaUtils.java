package io.microlam.aws.devops;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.microlam.aws.auth.AwsProfileRegionClientConfigurator;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.CreateAliasRequest;
import software.amazon.awssdk.services.lambda.model.CreateAliasResponse;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.GetAliasRequest;
import software.amazon.awssdk.services.lambda.model.GetAliasResponse;
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest;
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest.Builder;
import software.amazon.awssdk.services.lambda.model.ListFunctionsResponse;
import software.amazon.awssdk.services.lambda.model.PublishVersionRequest;
import software.amazon.awssdk.services.lambda.model.PublishVersionResponse;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException;
import software.amazon.awssdk.services.lambda.model.UpdateAliasRequest;
import software.amazon.awssdk.services.lambda.model.UpdateAliasResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeResponse;


public class LambdaUtils {

	private static Logger LOGGER = LoggerFactory.getLogger(LambdaUtils.class);

	public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	public static final String LOCAL_URL = "http://%s:%s/2015-03-31/functions/function/invocations";
	
	protected static LambdaClient lambdaClient = null;
	
	protected static final String DEFAULT_HOST = "localhost";
	protected static final int DEFAULT_PORT = 9000;

	public static LambdaClient getLambdaClient() {
		if (lambdaClient == null) {
			lambdaClient = createLambdaClient();
		}
		return lambdaClient;
	}

	public static LambdaClient createLambdaClient() {
		return AwsProfileRegionClientConfigurator.getInstance().configure(LambdaClient.builder()).build();
	}
	
	public static void updateLambdaCode(String[] allLambdas, String bucket, String s3Key) {
		updateLambdaCode(allLambdas, bucket, s3Key, Collections.emptySet(), null);
	}
	
	public static void updateLambdaCode(String[] allLambdas, String bucket, String s3Key, Set<String> versionedLambdas, String aliasName) {
		LambdaClient lambdaClient = getLambdaClient();
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
				boolean createVersion = versionedLambdas.contains(functionName);
				if (createVersion) {
					boolean success = false;
					PublishVersionResponse publishVersionResponse = null;
					LOGGER.info("Trying to publish version...");
					do {
						try {
							publishVersionResponse = lambdaClient.publishVersion(PublishVersionRequest.builder().functionName(functionName).codeSha256(resp.codeSha256()).build());
							LOGGER.info("done...");
							success = true;
						}
						catch(ResourceConflictException ex) {
							LOGGER.info(".");
							try {
								Thread.sleep(500);
							} 
							catch (InterruptedException e) {
							}
						}
					}
					while (! success);
					if (aliasName != null) {
						String functionVersion = publishVersionResponse.version();
						updateLambdaAlias(functionName, functionVersion, aliasName, lambdaClient);
					}
					else {
						LOGGER.info("No alias requested!");
					}
				}
				else {
					LOGGER.info("No version requested!");
				}

			}
			else {
				LOGGER.warn("Cannot update Code for Lambda {}", functionName);
			}
		}		
	}

	protected static void updateLambdaAlias(String functionName, String functionVersion, String aliasName,
			LambdaClient lambdaClient) {
		boolean aliasExists;
		try {
			GetAliasResponse getAliasResponse = lambdaClient.getAlias(GetAliasRequest.builder().functionName(functionName).name(aliasName).build());
			aliasExists = true;
			if (! getAliasResponse.sdkHttpResponse().isSuccessful()) {
				LOGGER.warn("Cannot get alias for Lambda {} and Alias {}", functionName, aliasName);
			}
		}
		catch(ResourceNotFoundException rnfex) {
			aliasExists = false;
		}
		if (! aliasExists) {
			CreateAliasResponse createAliasResponse = lambdaClient.createAlias(CreateAliasRequest.builder().functionName(functionName).functionVersion(functionVersion).name(aliasName).build());
			LOGGER.info("Alias created!");
			if (! createAliasResponse.sdkHttpResponse().isSuccessful()) {
				LOGGER.warn("Cannot create alias for Lambda {}, Alias {} and version {}", functionName, aliasName, functionVersion);
			}

		}
		else {
			UpdateAliasResponse updateAliasResponse =  lambdaClient.updateAlias(UpdateAliasRequest.builder().functionName(functionName).functionVersion(functionVersion).name(aliasName).build());
			LOGGER.info("Alias updated!");
			if (! updateAliasResponse.sdkHttpResponse().isSuccessful()) {
				LOGGER.warn("Cannot update alias for Lambda {}, Alias {} and version {}", functionName, aliasName, functionVersion);
			}
		}
	}

	public static void updateLambdaAlias(String[] allLambdas, String aliasName) {
		LambdaClient lambdaClient = getLambdaClient();

		for(String functionName : allLambdas) { 
			LOGGER.info("Updating alias for lambda: {}...", functionName);
			updateLambdaAlias(functionName, "$LATEST", aliasName, lambdaClient);
		}
	}
	
	public static String runPost(File file, String host, int port) throws IOException{
		OkHttpClient client = new OkHttpClient();
	    RequestBody body = RequestBody.create(file, JSON);
	    Request request = new Request.Builder()
	      .url(String.format(LOCAL_URL, host, String.valueOf(port)))
	      .post(body)
	      .build();
	  try (Response response = client.newCall(request).execute()) {
	    return response.body().string();
	  }
	}
	
	
	public static String localRunPost(File file) throws IOException {
		return localRunPost(file, DEFAULT_PORT);
	}
	
	public static String localRunPost(File file, int port) throws IOException {
		return runPost(file, DEFAULT_HOST, DEFAULT_PORT);
	}
}

