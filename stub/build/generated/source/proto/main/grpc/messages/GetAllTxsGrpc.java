package messages;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.39.0)",
    comments = "Source: ledger_api/grpc_requests.proto")
public final class GetAllTxsGrpc {

  private GetAllTxsGrpc() {}

  public static final String SERVICE_NAME = "messages.GetAllTxs";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<messages.HistoryRequest,
      messages.HistoryResponse> getGetHistoryMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetHistory",
      requestType = messages.HistoryRequest.class,
      responseType = messages.HistoryResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<messages.HistoryRequest,
      messages.HistoryResponse> getGetHistoryMethod() {
    io.grpc.MethodDescriptor<messages.HistoryRequest, messages.HistoryResponse> getGetHistoryMethod;
    if ((getGetHistoryMethod = GetAllTxsGrpc.getGetHistoryMethod) == null) {
      synchronized (GetAllTxsGrpc.class) {
        if ((getGetHistoryMethod = GetAllTxsGrpc.getGetHistoryMethod) == null) {
          GetAllTxsGrpc.getGetHistoryMethod = getGetHistoryMethod =
              io.grpc.MethodDescriptor.<messages.HistoryRequest, messages.HistoryResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetHistory"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  messages.HistoryRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  messages.HistoryResponse.getDefaultInstance()))
              .setSchemaDescriptor(new GetAllTxsMethodDescriptorSupplier("GetHistory"))
              .build();
        }
      }
    }
    return getGetHistoryMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static GetAllTxsStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<GetAllTxsStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<GetAllTxsStub>() {
        @java.lang.Override
        public GetAllTxsStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new GetAllTxsStub(channel, callOptions);
        }
      };
    return GetAllTxsStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static GetAllTxsBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<GetAllTxsBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<GetAllTxsBlockingStub>() {
        @java.lang.Override
        public GetAllTxsBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new GetAllTxsBlockingStub(channel, callOptions);
        }
      };
    return GetAllTxsBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static GetAllTxsFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<GetAllTxsFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<GetAllTxsFutureStub>() {
        @java.lang.Override
        public GetAllTxsFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new GetAllTxsFutureStub(channel, callOptions);
        }
      };
    return GetAllTxsFutureStub.newStub(factory, channel);
  }

  /**
   */
  public static abstract class GetAllTxsImplBase implements io.grpc.BindableService {

    /**
     */
    public void getHistory(messages.HistoryRequest request,
        io.grpc.stub.StreamObserver<messages.HistoryResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetHistoryMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getGetHistoryMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                messages.HistoryRequest,
                messages.HistoryResponse>(
                  this, METHODID_GET_HISTORY)))
          .build();
    }
  }

  /**
   */
  public static final class GetAllTxsStub extends io.grpc.stub.AbstractAsyncStub<GetAllTxsStub> {
    private GetAllTxsStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected GetAllTxsStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new GetAllTxsStub(channel, callOptions);
    }

    /**
     */
    public void getHistory(messages.HistoryRequest request,
        io.grpc.stub.StreamObserver<messages.HistoryResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetHistoryMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class GetAllTxsBlockingStub extends io.grpc.stub.AbstractBlockingStub<GetAllTxsBlockingStub> {
    private GetAllTxsBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected GetAllTxsBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new GetAllTxsBlockingStub(channel, callOptions);
    }

    /**
     */
    public messages.HistoryResponse getHistory(messages.HistoryRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetHistoryMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class GetAllTxsFutureStub extends io.grpc.stub.AbstractFutureStub<GetAllTxsFutureStub> {
    private GetAllTxsFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected GetAllTxsFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new GetAllTxsFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<messages.HistoryResponse> getHistory(
        messages.HistoryRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetHistoryMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_GET_HISTORY = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final GetAllTxsImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(GetAllTxsImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_GET_HISTORY:
          serviceImpl.getHistory((messages.HistoryRequest) request,
              (io.grpc.stub.StreamObserver<messages.HistoryResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class GetAllTxsBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    GetAllTxsBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return messages.GrpcRequests.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("GetAllTxs");
    }
  }

  private static final class GetAllTxsFileDescriptorSupplier
      extends GetAllTxsBaseDescriptorSupplier {
    GetAllTxsFileDescriptorSupplier() {}
  }

  private static final class GetAllTxsMethodDescriptorSupplier
      extends GetAllTxsBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    GetAllTxsMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (GetAllTxsGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new GetAllTxsFileDescriptorSupplier())
              .addMethod(getGetHistoryMethod())
              .build();
        }
      }
    }
    return result;
  }
}
