package node

import ledger.Ledger

var SHARDS = 4

class Node(id: Int){
    val id: Int = id
    val my_shard = id % SHARDS
    val ledger: Ledger? = null

}