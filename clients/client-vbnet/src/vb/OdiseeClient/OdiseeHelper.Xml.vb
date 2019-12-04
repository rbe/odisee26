'
' Odisee(R)
' Copyright (C) 2011-2013 art of coding UG, http://www.art-of-coding.eu
' Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
'

Option Strict On
Option Explicit On

Imports System.IO
Imports System.Text
Imports System.Xml

Namespace Helper

    ''' <summary>
    ''' Helper for processing XML.
    ''' </summary>
    ''' <remarks></remarks>
    Public Class Xml

        ''' <summary>
        ''' Pretty print a XML document.
        ''' </summary>
        ''' <param name="xmlDocument"></param>
        ''' <returns></returns>
        ''' <remarks></remarks>
        Public Shared Function prettyPrint(ByRef xmlDocument As XmlDocument) As String
            Dim xmlReader As XmlNodeReader = New XmlNodeReader(xmlDocument)
            Dim stringWriter As StringWriter = New StringWriter()
            Dim xmlWriter As XmlTextWriter = New XmlTextWriter(stringWriter)
            ' Set formatting options
            xmlWriter.Formatting = Formatting.Indented
            xmlWriter.Indentation = 1
            xmlWriter.IndentChar = CChar("  ")
            ' Write the document formatted
            xmlWriter.WriteNode(xmlReader, True)
            '
            Return stringWriter.ToString
        End Function

        ''' <summary>
        ''' Append an instruction to //request/instructions[last()] element.
        ''' </summary>
        ''' <param name="requestElement">XmlElement</param>
        ''' <param name="xmlElement">The XmlElement to append to requestElement's last instruction element</param>
        ''' <remarks></remarks>
        Public Shared Sub appendToLastInstruction(ByRef requestElement As XmlElement, ByRef xmlElement As XmlElement)
            Dim nodes As XmlNodeList = requestElement.SelectNodes(OdiseeConstant.LAST_INSTRUCTION_OF_REQUEST)
            Dim item As XmlNode = nodes.Item(0)
            item.AppendChild(xmlElement)
        End Sub

        ''' <summary>
        ''' Append an instruction to request's //request[last()]/post-process/instructions[last()] element. 
        ''' </summary>
        ''' <param name="requestElement"></param>
        ''' <param name="xmlElement"></param>
        ''' <remarks></remarks>
        Public Shared Sub appendPostProcessInstruction(ByRef requestElement As XmlElement, ByRef xmlElement As XmlElement)
            ' Get (or create) <post-process> element
            Dim postProcessNode As XmlNode = requestElement.SelectSingleNode("post-process")
            If IsNothing(postProcessNode) Then
                postProcessNode = requestElement.OwnerDocument.CreateElement("post-process")
                requestElement.AppendChild(postProcessNode)
            End If
            ' Get (or create) <instructions> element
            Dim nodes As XmlNodeList = postProcessNode.SelectNodes(OdiseeConstant.LAST_INSTRUCTION_OF_POSTPROCESS)
            Dim instructionsElement As XmlNode
            If nodes.Count > 0 Then
                instructionsElement = nodes.Item(0)
            Else
                instructionsElement = postProcessNode.OwnerDocument.CreateElement("instructions")
                postProcessNode.AppendChild(instructionsElement)
            End If
            ' Append instruction
            instructionsElement.AppendChild(xmlElement)
        End Sub

    End Class

End Namespace
