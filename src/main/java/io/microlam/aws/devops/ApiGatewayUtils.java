package io.microlam.aws.devops;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.microlam.aws.auth.AwsProfileRegionClientConfigurator;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.apigateway.model.RestApi;

public class ApiGatewayUtils {

	private static Logger LOGGER = LoggerFactory.getLogger(ApiGatewayUtils.class);
	public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

	public static String INVOKE_URL_PATTERN = "https://%s.execute-api.%s.amazonaws.com/%s";
	
	protected static ApiGatewayClient apiGatewayClient = null;

	public static ApiGatewayClient getApiGatewayClient() {
		if (apiGatewayClient == null) {
			apiGatewayClient = createApiGatewayClient();
		}
		return apiGatewayClient;
	}

	public static ApiGatewayClient createApiGatewayClient() {
		return AwsProfileRegionClientConfigurator.getInstance().configure(ApiGatewayClient.builder()).build();
	}

	public static String getInvokeUrl(String apiName, String stageName) {
		GetRestApisResponse getRestApisResponse = getApiGatewayClient().getRestApis();
		String restApiId = null;
		for(RestApi restApi:  getRestApisResponse.items()) {
			LOGGER.info("Found RestApi: " + restApi.name() + " (" + restApi.id() + ")");
			if (restApi.name().equals(apiName)) {
				restApiId = restApi.id();
				break;
			}
		}
		if (restApiId != null) {
			return String.format(INVOKE_URL_PATTERN, restApiId, AwsProfileRegionClientConfigurator.getRegion().id(), stageName);
		}
		throw new RuntimeException("Cannot find RestApi with name: " + apiName);
	}
	
	
	public static String runPost(String invokeUrl, File file) throws IOException {
		OkHttpClient client = new OkHttpClient();
		RequestBody body = RequestBody.create(file, JSON);
		Request request = new Request.Builder()
	      .url(invokeUrl)
	      .post(body)
	      .build();
		try (Response response = client.newCall(request).execute()) {
		    return response.body().string();
		}
	}
}
