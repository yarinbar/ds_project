package com.kotlingrpc.demoGrpc.generated.main.grpckt.com.kotlingrpc.demoGrpc
import com.kotlingrpc.demoGrpc.GreeterGrpcKt
import com.kotlingrpc.demoGrpc.HelloReply
import com.kotlingrpc.demoGrpc.HelloRequest

import messages.GetAllTxsGrpcKt
import messages.HistoryResponse
import messages.HistoryRequest
import messages.GetAllTxsGrpc

import io.grpc.Server
import io.grpc.ServerBuilder
import org.springframework.stereotype.Component

class HelloWorldServer(private val port: Int) {
    val server: Server = ServerBuilder
        .forPort(port)
        .addService(HelloWorldService())
        .addService(GetAllTransactions())
        .build()

    fun start() {
        server.start()
        println("Server started, listening on $port")
        Runtime.getRuntime().addShutdownHook(
            Thread {
                println("*** shutting down gRPC server since JVM is shutting down")
                this@HelloWorldServer.stop()
                println("*** server shut down")
            }
        )
    }

    private fun stop() {
        server.shutdown()
    }

    fun blockUntilShutdown() {
        server.awaitTermination()
    }

    private class HelloWorldService : GreeterGrpcKt.GreeterCoroutineImplBase() {
        override suspend fun sayHello(request: HelloRequest) = HelloReply
            .newBuilder()
            .setMessage("Hello ${request.name}")
            .build()
    }

    private class GetAllTransactions : GetAllTxsGrpcKt.GetAllTxsCoroutineImplBase(){
        override suspend fun getHistory(request: HistoryRequest) = HistoryResponse
            .newBuilder()
            .setMessage("Hello This ${request.addr} ${request.n}")
            .build()
    }
}
fun main() {
    val port = System.getenv("PORT")?.toInt() ?: 50051
    val server = HelloWorldServer(port)
    server.start()
    server.blockUntilShutdown()
}