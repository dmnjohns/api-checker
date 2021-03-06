<?xml version="1.0" encoding="UTF-8"?>
<!--
  raxRoles.xsl

  This stylesheet is responsible for transforming rax:roles found
  in resource or method attributes into header params
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:wadl="http://wadl.dev.java.net/2009/02"
                xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                xmlns:rax="http://docs.rackspace.com/api">
    <xsl:template match="node() | @*">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="wadl:resource">
        <xsl:param name="roles" as="xsd:string*" select="()"/>

        <xsl:variable name="allRoles" as="xsd:string*">
            <xsl:sequence select="$roles"/>
            <xsl:if test="@rax:roles">
                <xsl:sequence select="tokenize(@rax:roles,' ')"/>
            </xsl:if>
        </xsl:variable>

        <xsl:copy>
            <xsl:apply-templates select="@* | node()">
                <xsl:with-param name="roles" select="$allRoles"/>
            </xsl:apply-templates>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="wadl:method">
        <xsl:param name="roles" as="xsd:string*" select="()"/>

        <xsl:variable name="allRoles" as="xsd:string*">
          <xsl:sequence select="$roles"/>
          <xsl:if test="@rax:roles">
              <xsl:sequence select="tokenize(@rax:roles,' ')"/>
          </xsl:if>
        </xsl:variable>
        <xsl:copy>
          <xsl:apply-templates select="@*"/>
          <xsl:if test="not(wadl:request)">
              <wadl:request>
                  <xsl:call-template name="generateRoles">
                      <xsl:with-param name="roles" select="$allRoles"/>
                  </xsl:call-template>
              </wadl:request>
          </xsl:if>
          <xsl:apply-templates select="node()">
              <xsl:with-param name="roles" select="$allRoles"/>
          </xsl:apply-templates>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="wadl:request">
        <xsl:param name="roles" as="xsd:string*" select="()"/>
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xsl:if test="count($roles) != 0">
               <xsl:call-template name="generateRoles">
                  <xsl:with-param name="roles" select="$roles"/>
               </xsl:call-template>
            </xsl:if>
           <xsl:apply-templates select="node()">
              <xsl:with-param name="roles" select="$roles"/>
           </xsl:apply-templates>
        </xsl:copy>
    </xsl:template>

    <xsl:template name="generateRoles">
        <xsl:param name="roles" as="xsd:string*" select="()"/>
        <xsl:if test="not('#all' = $roles)">
            <xsl:for-each select="$roles">
                <wadl:param name="X-ROLES" style="header" rax:code="403"
                            rax:message="You are forbidden to perform the operation" type="xsd:string" required="true">
                    <xsl:attribute name="fixed" select="."/>
                </wadl:param>
            </xsl:for-each>
        </xsl:if>

    </xsl:template>
</xsl:stylesheet>
