package models

import java.util.Date

import java.net._

/**
 * Keeps track of server start time
 * Used in Global Object
 *
 */
object ServerStartTime {
  var startTime: Date=null
  var url: String="http://localhost:9000/"
  var ip: String=InetAddress.getLocalHost.getHostAddress
}
