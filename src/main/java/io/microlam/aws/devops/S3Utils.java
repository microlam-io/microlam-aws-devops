package io.microlam.aws.devops;


import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.microlam.aws.auth.AwsProfileRegionClientConfigurator;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

public class S3Utils {
	
	private static Logger LOGGER = LoggerFactory.getLogger(S3Utils.class);
	
	protected static S3Client s3Client = null;
	protected static S3Client s3ClientTA = null;
	
	public static S3Client getS3Client(boolean transferAcceleration) {
		if (transferAcceleration) {
			if (s3ClientTA == null) {
				s3ClientTA = createS3Client(transferAcceleration);
			}
			return s3ClientTA;
		}
		else {
			if (s3Client == null) {
				s3Client = createS3Client(transferAcceleration);
			}
			return s3Client;
		}
	}

	public static S3Client createS3Client() {
		return AwsProfileRegionClientConfigurator.getInstance().configure(S3Client.builder()).build();
	}

	public static S3Client createS3Client(boolean transferAcceleration) {
		return AwsProfileRegionClientConfigurator.getInstance().configure(S3Client.builder().accelerate(transferAcceleration))
				.build();
	}

	public static String uploadFileToS3(File file, String bucket) {
		return uploadFileToS3(file, bucket, false);
	}
	
	public static String uploadFileToS3(File file, String bucket, boolean transferAcceleration) {
		LOGGER.info("Deploying file {} to S3 bucket '{}'...",  file.getName(), bucket);
		Path path = file.toPath();
		
		PutObjectResponse por = getS3Client(transferAcceleration).putObject((PutObjectRequest) PutObjectRequest.builder().bucket(bucket).key(file.getName()).build(), path);
		if (por.sdkHttpResponse().isSuccessful()) {
			LOGGER.info("ok! File uploaded to {}", "s3://"+ bucket + "/" + file.getName());
			return file.getName();
		}
		throw new RuntimeException("Cannot upload file "+ file.getName() + " to S3 bucket '"+ bucket + "'");
	}

	public static String uploadFileToS3(File file, String bucket, int maxThreads) {
		return uploadFileToS3(file, bucket, false, maxThreads);
	}
	
	public static String uploadFileToS3(File file, String bucket, boolean transferAcceleration, int maxThreads) {
		LOGGER.info("Deploying file {} to S3 bucket '{}'...",  file.getName(), bucket);
		CreateMultipartUploadResponse response = getS3Client(transferAcceleration).createMultipartUpload(CreateMultipartUploadRequest.builder()
				.bucket(bucket)
				.key(file.getName())
				.build()
				);
		String uploadId = response.uploadId();
		LOGGER.debug("UploadId = {}", uploadId);

		final long fiveMega = 5*1024*1024;
		long size = file.length();
		if (size < fiveMega) {
			LOGGER.info("File too small for Multi-Part upload");
			return uploadFileToS3(file, bucket, transferAcceleration);
		}
		int count = (int) (size / fiveMega)+1;
		int threadCount = Math.min(maxThreads, count);
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		LOGGER.info("Using pool of {} threads", threadCount);
		
		UploadPartResponse[] results = new  UploadPartResponse[count];
		CompletedPart[] resultParts = new CompletedPart[count];
		for (int i = 1; i <= count; i++) {
			final int partNumber = i;
            Runnable worker = new Runnable() {

				@Override
				public void run() {
					try {
						FileInputStream fis = new FileInputStream(file);
						long offset = (partNumber-1)*fiveMega;
						long contentLength = Math.min(size-offset, fiveMega);
						fis.getChannel().position(offset);
						LOGGER.info("Starting upload part {} from offset = {} with contentLength = {}", partNumber, offset, contentLength);
						UploadPartResponse uprs = getS3Client(transferAcceleration).uploadPart(UploadPartRequest.builder()
								.bucket(bucket)
								.key(file.getName())
								.uploadId(uploadId)
								.partNumber(partNumber)
								.contentLength(contentLength)
								.build(), RequestBody.fromInputStream(fis, contentLength));
						results[partNumber-1] = uprs;
						resultParts[partNumber-1] = CompletedPart.builder().partNumber(partNumber).eTag(uprs.eTag()).build();
						LOGGER.info("Upload part {} : {}", partNumber, uprs.sdkHttpResponse().isSuccessful());

					}
					catch (Throwable th) {
						th.printStackTrace();
						LOGGER.error("Upload part {} error! {}", partNumber, th.getMessage());
					}
				}
            	
            };
            executor.execute(worker);
        }
		try {
			executor.shutdown();
			boolean terminated = executor.awaitTermination(1, TimeUnit.DAYS);
			LOGGER.info("Terminated: {}", terminated);
			if (terminated) {
				CompleteMultipartUploadRequest.Builder builder = CompleteMultipartUploadRequest.builder()
						.bucket(bucket)
						.key(file.getName())
						.uploadId(uploadId)
						.multipartUpload(CompletedMultipartUpload.builder().parts(resultParts).build());
						
				getS3Client(transferAcceleration).completeMultipartUpload(builder.build());
				LOGGER.info("ok! File uploaded to {}", "s3://"+ bucket + "/" + file.getName());
				return file.getName();
			}
			else {
				throw new RuntimeException("Execution not completed in time...");
			}
		}
		catch(Throwable th) {
			throw new RuntimeException("Unexpected Exception", th);
		}
	}
}
