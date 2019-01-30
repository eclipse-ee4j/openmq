# Open Message Queue Feature Matrix

<table width="95%" border="0" cellpadding="0" cellspacing="0" class="generic1">
  <thead>
    <tr valign="middle">
      <td width="43%"><div>
        <div align="center">Feature</div>
      </div></td>
      <td width="2%">&nbsp;</td>
      <td width="55%"><div>
        <div align="center">Description</div>
      </div></td>
    </tr>
  </thead>
  <tbody>
    <tr valign="middle">
      <td><div>JMS 1.1 Specification </div></td>
      <td>&nbsp;</td>
      <td><div>Covers basic messaging requirements. Industry supported Java API. </div></td>
    </tr>
    <tr valign="middle">
      <td><div class="generic1" id="body">Java EE 1.4 support (JCA 1.5 Resource Adapter) </div></td>
      <td>&nbsp;</td>
      <td><div>Full support for all Java EE required interfaces. Allows integration with Java EE application servers that conform to the 1.4 or higher specification. </div></td>
    </tr>
    <tr valign="middle">
      <td><div>Integrated File store</div></td>
      <td>&nbsp;</td>
      <td><div>For highest performance, efficient, embedded file store </div></td>
    </tr>
    <tr valign="middle">
      <td><div>JDBC File store </div></td>
      <td>&nbsp;</td>
      <td><div>Tested databases include Oracle, MySQL, Postres-SQL, Java DB (Derby) </div></td>
    </tr>
    <tr valign="middle">
      <td><div>High Availability (Automatic Takeover) </div></td>
      <td>&nbsp;</td>
      <td><div>HA via JDBC data store configuration. For best resilience and full availability. Tested with Oracle and MySQL</div></td>
    </tr>
    <tr valign="middle">
      <td><div>High Availability (Active / Standby)  </div></td>
      <td>&nbsp;</td>
      <td><div>With Sun Cluster you can deploy for maximum system performance and availability with availability even in the event of a  server failure </div></td>
    </tr>
    <tr valign="middle">
      <td><div>Distributed cluster support (Service availability)</div></td>
      <td>&nbsp;</td>
      <td><div>Multiple broker node support with no client connection restrictions. Provides service availability, high performance, low administration overhead. </div></td>
    </tr>
    <tr valign="middle">
      <td><div>Publish and Subscribe Messaging </div></td>
      <td>&nbsp;</td>
      <td><div>Shared topic subscriptions, flexible distribution options </div></td>
    </tr>
    <tr valign="middle">
      <td><div>Wild card Topics </div></td>
      <td>&nbsp;</td>
      <td><div>Allows for publish or subscribe with wild-card syntax. <em>New feature in version 4.2 </em></div></td>
    </tr>
    <tr valign="middle">
      <td><div>Range of message delivery modes </div></td>
      <td>&nbsp;</td>
      <td><div>Once and only once, At most once, at least once, non-acknowledged, duplicates okay </div></td>
    </tr>
    <tr valign="middle">
      <td><div>Full range of Transaction support </div></td>
      <td>&nbsp;</td>
      <td><div>XA support for extended transaction context</div></td>
    </tr>
    <tr valign="middle">
      <td><div>Dead Message Queue</div></td>
      <td>&nbsp;</td>
      <td><div>If messages expire, are undeliverable, they are moved to a destination for administrative processing </div></td>
    </tr>
    <tr valign="middle">
      <td><div>Message Compression </div></td>
      <td>&nbsp;</td>
      <td><div>Allows messages to be compressed for transmission across the client - server interface as well as storing it in compressed form. </div></td>
    </tr>
    <tr valign="middle">
      <td><div>Port optimization </div></td>
      <td>&nbsp;</td>
      <td><div>Portmapper allows multiple protocols through single port. Reduces fire-wall complexity </div></td>
    </tr>
    <tr valign="middle">
      <td><div>Flow Control </div></td>
      <td>&nbsp;</td>
      <td><div>When destinations reach configured thresholds, production is throttled back. A range of configuration options is provided </div></td>
    </tr>
    <tr valign="middle">
      <td><div>Access Control </div></td>
      <td>&nbsp;</td>
      <td><div>Administer can control which user IDs have permissions for various operations </div></td>
    </tr>
    <tr valign="middle">
      <td><div>Message Security </div></td>
      <td>&nbsp;</td>
      <td><div>HTTPS, SSL, TLS support for message security </div></td>
    </tr>
    <tr valign="middle">
      <td><div>Authentication</div></td>
      <td>&nbsp;</td>
      <td><div>LDAP or file based credential support. JAAS support for custom authorization integration </div></td>
    </tr>
    <tr valign="middle">
      <td><div>Message Selectors</div></td>
      <td>&nbsp;</td>
      <td><div>Allows consumer to query messages based on criteria </div></td>
    </tr>
    <tr valign="middle">
      <td><div>XML Schema Validation </div></td>
      <td>&nbsp;</td>
      <td><div>Prevents invalid XML messages from being produced into a destination. <em>New feature in Version 4.2 </em></div></td>
    </tr>
    <tr valign="middle">
      <td><div>C-API</div></td>
      <td>&nbsp;</td>
      <td><div>Supports c-integration, Solaris (SPARC/x86), Linux, Windows. </div></td>
    </tr>
    <tr valign="middle">
      <td><div>XA support via C-API </div></td>
      <td>&nbsp;</td>
      <td><div>XA support for C-API, tested with Tuxedo Transaction Manager. <em>New feature  in version 4.2 </em></div></td>
    </tr>
    <tr valign="middle">
      <td><div>JMS over HTTP/SOAP </div></td>
      <td>&nbsp;</td>
      <td><div>Allows JMS messaging through firewall tunneling </div></td>
    </tr>
    <tr valign="middle">
      <td><div>JMS over WebSocket</div></td>
      <td>&nbsp;</td>
      <td><div>Allows MQ JMS clients connect to broker over WebSocket (<a href="https://javaee.github.io/openmq/www/5.0.1/ws.html">details</a>)</div></td>
    </tr>
    <tr valign="middle">
      <td><div>STOMP over WebSocket</div></td>
      <td>&nbsp;</td>
      <td><div>Allows STOMP clients connect to broker over WebSocket (<a href="https://javaee.github.io/openmq/www/5.0.1/ws.html">details</a>)</div></td>
    </tr>
    <tr valign="middle">
      <td><div>JMS Bridge Service</div></td>
      <td>&nbsp;</td>
      <td><div>Allows Message Queue broker to communicate with clients of the external JMS providers</div></td>
    </tr>
    <tr valign="middle">
      <td><div>STOMP Bridge Service</div></td>
      <td>&nbsp;</td>
      <td><div>Allows STOMP clients connect to broker over TCP</div></td>
    </tr>
    <tr valign="middle">
      <td><div>GUI based administration utility  </div></td>
      <td>&nbsp;</td>
      <td><div>Basic administration command support </div></td>
    </tr>
    <tr valign="middle">
      <td><div>Complete control via command line </div></td>
      <td>&nbsp;</td>
      <td><div>All administration commands available through scriptable commands </div></td>
    </tr>
    <tr valign="middle">
      <td><div>Complete JMX interface</div></td>
      <td>&nbsp;</td>
      <td><div>Allows integration with existing administration and monitoring tools or custom administration </div></td>
    </tr>
    <tr valign="middle">
      <td><div>JCA 1.5 Resource Adapter support</div></td>
      <td>&nbsp;</td>
      <td><div>Embedded Resource Adapter for GlassFish; JMSJCA support for extended integration support (WebLogic, WebSphere, JBOSS, Etc.); GenericResourceAdapter support for GlassFish integration. </div></td>
    </tr>
    <tr valign="middle">
      <td><div>Distributed destinations </div></td>
      <td>&nbsp;</td>
      <td><div>Message destinations are shared between broker cluster nodes for better performance and load balancing </div></td>
    </tr>
    <tr valign="middle">
      <td><div>Solaris Service Management Facility Integration </div></td>
      <td>&nbsp;</td>
      <td><div>Allows common configuration management integration</div></td>
    </tr>
    <tr valign="middle">
      <td><div>Internationalization</div></td>
      <td>&nbsp;</td>
      <td><div>All message strings can be localized</div></td>
    </tr>
  </tbody>
</table>

