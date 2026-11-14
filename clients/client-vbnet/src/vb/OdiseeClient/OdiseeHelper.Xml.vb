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
        ''' Pretty print a XML document as UTF-8.
        ''' </summary>
        Public Shared Function prettyPrint(ByRef xmlDocument As XmlDocument) As String
            Using memoryStream As New MemoryStream()
                Dim settings As New XmlWriterSettings()
                settings.Encoding = New UTF8Encoding(False)
                settings.Indent = True
                settings.IndentChars = "  "
                settings.OmitXmlDeclaration = True
                Using xmlWriter As XmlWriter = XmlWriter.Create(memoryStream, settings)
                    xmlDocument.Save(xmlWriter)
                End Using
                Return Encoding.UTF8.GetString(memoryStream.ToArray())
            End Using
        End Function

        ''' <summary>
        ''' Append an instruction to //request/instructions[last()] element.
        ''' </summary>
        Public Shared Sub appendToLastInstruction(ByRef requestElement As XmlElement, ByRef xmlElement As XmlElement)
            Dim nodes As XmlNodeList = requestElement.SelectNodes(OdiseeConstant.LAST_INSTRUCTION_OF_REQUEST)
            Dim item As XmlNode = nodes.Item(0)
            item.AppendChild(xmlElement)
        End Sub

        ''' <summary>
        ''' Append an action directly to request's //request/post-process element (v2 request schema).
        ''' </summary>
        Public Shared Sub appendPostProcessInstruction(ByRef requestElement As XmlElement, ByRef xmlElement As XmlElement)
            Dim postProcessNode As XmlNode = requestElement.SelectSingleNode("post-process")
            If IsNothing(postProcessNode) Then
                postProcessNode = requestElement.OwnerDocument.CreateElement("post-process")
                Dim outputNode As XmlNode = requestElement.SelectSingleNode("output")
                If outputNode IsNot Nothing Then
                    requestElement.InsertBefore(postProcessNode, outputNode)
                Else
                    requestElement.AppendChild(postProcessNode)
                End If
            End If
            postProcessNode.AppendChild(xmlElement)
        End Sub

    End Class

End Namespace
