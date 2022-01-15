package io.microlam.aws.devops.cdk;

import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public abstract class AbstractStack extends Stack {
	
    public AbstractStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public AbstractStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);
        init(scope, id, props);
    }
    
    
    // The code that defines your stack goes here

    // example resource
    // final Queue queue = Queue.Builder.create(this, "HelloCdkQueue")
    //         .visibilityTimeout(Duration.seconds(300))
    //         .build();    
    protected abstract void init(final Construct scope, final String id, final StackProps props);
}
