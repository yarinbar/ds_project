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
	var port = System.getenv("PORT")?.toInt() ?: 50051
	var shard: Int = (System.getenv("SHARD") ?: "0").toInt()
	val server = HelloWorldServer(ip, shard, port)

	server.start()
	server.blockUntilShutdown()

}
