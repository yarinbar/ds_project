package server

import grpc_server.HelloWorldServer
import membership.Membership
import org.apache.zookeeper.ZooKeeper
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json
import zookeeper.kotlin.ZookeeperKtClient
import java.io.File

import java.net.DatagramSocket;
import java.net.InetAddress;

@SpringBootApplication
class SpringBootBoilerplateApplication


suspend fun main(args: Array<String>) {

	var ip : String
	DatagramSocket().use { socket ->
		socket.connect(InetAddress.getByName("8.8.8.8"), 10002)
		ip = socket.localAddress.hostAddress
	}

	runApplication<SpringBootBoilerplateApplication>(*args)
	val server = HelloWorldServer(ip)

	server.start()
	server.blockUntilShutdown()

}
