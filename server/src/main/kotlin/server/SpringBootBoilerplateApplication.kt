package server

import grpc_server.HelloWorldServer
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SpringBootBoilerplateApplication

fun main(args: Array<String>) {

	runApplication<SpringBootBoilerplateApplication>(*args)
	val port = System.getenv("PORT")?.toInt() ?: 50051
	val server = HelloWorldServer(port)
	server.start()
	server.blockUntilShutdown()

}
