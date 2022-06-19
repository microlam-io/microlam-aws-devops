package io.microlam.aws.devops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.microlam.aws.auth.AwsProfileRegionClientConfigurator;
import software.amazon.awssdk.services.sts.StsClient;

public class StsUtils {

	private static Logger LOGGER = LoggerFactory.getLogger(StsUtils.class);
	
	protected static StsClient stsClient = null;
	
	public static StsClient getStsClient() {
		if (stsClient == null) {
			stsClient = createStsClient();
		}
		return stsClient;
	}

	public static StsClient createStsClient() {
		return AwsProfileRegionClientConfigurator.getInstance().configure(StsClient.builder()).build();
	}

	public static String getAccountId() {
		return getStsClient().getCallerIdentity().account();
	}
}
