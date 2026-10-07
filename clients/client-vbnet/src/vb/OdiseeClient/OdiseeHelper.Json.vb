'
' Odisee(R)
' Copyright (C) 2011-2013 art of coding UG, http://www.art-of-coding.eu
' Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
'

Option Strict On
Option Explicit On

Imports System.Collections.Generic
Imports System.Text
Imports System.Xml

Namespace Helper

    ''' <summary>
    ''' Converts an Odisee XML request into the JSON document the service accepts.
    ''' </summary>
    Public Class Json

        Private Sub New()
        End Sub

        Public Shared Function FromXml(ByVal document As XmlDocument) As String
            If document Is Nothing OrElse document.DocumentElement Is Nothing Then
                Throw New ArgumentException("XML request root must be odisee")
            End If
            If LocalName(document.DocumentElement) <> "odisee" Then
                Throw New ArgumentException("XML request root must be odisee")
            End If
            Dim sb As New StringBuilder()
            Dim states As New Stack(Of Boolean)()
            StartObjectValue(sb, states)
            Key(sb, states, "request")
            StartArrayValue(sb, states)
            Dim child As XmlElement
            Dim postProcess As XmlElement = Nothing
            Dim response As XmlElement = Nothing
            For Each child In Children(document.DocumentElement)
                Select Case LocalName(child)
                    Case "request"
                        WriteRequest(sb, states, child)
                    Case "post-process"
                        postProcess = child
                    Case "response"
                        response = child
                End Select
            Next
            EndArray(sb, states)
            If postProcess IsNot Nothing Then
                Key(sb, states, "postProcess")
                WritePostProcess(sb, states, postProcess)
            End If
            If response IsNot Nothing Then
                Key(sb, states, "response")
                WriteResponse(sb, states, response)
            End If
            EndObject(sb, states)
            Return sb.ToString()
        End Function

        Private Shared Sub WriteRequest(ByVal sb As StringBuilder, ByVal states As Stack(Of Boolean), ByVal element As XmlElement)
            StartObjectElement(sb, states)
            WriteAttributes(sb, states, element)
            Dim instructions As New List(Of XmlElement)()
            Dim sawInstructions As Boolean = False
            Dim requestPostProcess As XmlElement = Nothing
            Dim child As XmlElement
            For Each child In Children(element)
                Select Case LocalName(child)
                    Case "template", "archive", "group"
                        Key(sb, states, LocalName(child))
                        StartObjectValue(sb, states)
                        WriteAttributes(sb, states, child)
                        EndObject(sb, states)
                    Case "instructions"
                        sawInstructions = True
                        Dim instruction As XmlElement
                        For Each instruction In Children(child)
                            instructions.Add(instruction)
                        Next
                    Case "post-process"
                        requestPostProcess = child
                End Select
            Next
            If sawInstructions Then
                Key(sb, states, "instructions")
                StartArrayValue(sb, states)
                Dim instruction As XmlElement
                For Each instruction In instructions
                    WriteInstruction(sb, states, instruction)
                Next
                EndArray(sb, states)
            End If
            If requestPostProcess IsNot Nothing Then
                Key(sb, states, "postProcess")
                WritePostProcess(sb, states, requestPostProcess)
            End If
            EndObject(sb, states)
        End Sub

        Private Shared Sub WriteInstruction(ByVal sb As StringBuilder, ByVal states As Stack(Of Boolean), ByVal element As XmlElement)
            StartObjectElement(sb, states)
            Key(sb, states, "instruction")
            StringValue(sb, LocalName(element))
            WriteAttributes(sb, states, element)
            If LocalName(element) = "macro" Then
                Dim parameters As New List(Of XmlElement)()
                Dim child As XmlElement
                For Each child In Children(element)
                    If LocalName(child) = "parameter" Then
                        parameters.Add(child)
                    End If
                Next
                If parameters.Count > 0 Then
                    Key(sb, states, "parameter")
                    StartArrayValue(sb, states)
                    Dim parameter As XmlElement
                    For Each parameter In parameters
                        StartObjectElement(sb, states)
                        Key(sb, states, "value")
                        Dim attribute As String = parameter.GetAttribute("value")
                        If attribute Is Nothing OrElse attribute.Length = 0 Then
                            StringValue(sb, DirectText(parameter))
                        Else
                            StringValue(sb, attribute)
                        End If
                        EndObject(sb, states)
                    Next
                    EndArray(sb, states)
                End If
            Else
                Dim text As String = DirectText(element)
                If text.Length > 0 Then
                    Key(sb, states, "value")
                    StringValue(sb, text)
                End If
            End If
            EndObject(sb, states)
        End Sub

        Private Shared Sub WritePostProcess(ByVal sb As StringBuilder, ByVal states As Stack(Of Boolean), ByVal element As XmlElement)
            StartObjectValue(sb, states)
            Key(sb, states, "action")
            StartArrayValue(sb, states)
            WriteActions(sb, states, element)
            EndArray(sb, states)
            EndObject(sb, states)
        End Sub

        Private Shared Sub WriteActions(ByVal sb As StringBuilder, ByVal states As Stack(Of Boolean), ByVal element As XmlElement)
            Dim child As XmlElement
            For Each child In Children(element)
                Dim name As String = LocalName(child)
                If name = "action" Then
                    WriteAction(sb, states, child)
                ElseIf name = "instructions" Then
                    WriteActions(sb, states, child)
                End If
            Next
        End Sub

        Private Shared Sub WriteAction(ByVal sb As StringBuilder, ByVal states As Stack(Of Boolean), ByVal element As XmlElement)
            StartObjectElement(sb, states)
            WriteAttributes(sb, states, element)
            Dim steps As List(Of XmlElement) = Children(element)
            If steps.Count > 0 Then
                Key(sb, states, "content")
                StartArrayValue(sb, states)
                Dim child As XmlElement
                For Each child In steps
                    StartObjectElement(sb, states)
                    Key(sb, states, "element")
                    StringValue(sb, LocalName(child))
                    If LocalName(child) = "input" AndAlso child.HasAttribute("file") AndAlso Not child.HasAttribute("filename") Then
                        Key(sb, states, "filename")
                        StringValue(sb, child.GetAttribute("file"))
                    Else
                        WriteAttributes(sb, states, child)
                    End If
                    EndObject(sb, states)
                Next
                EndArray(sb, states)
            End If
            EndObject(sb, states)
        End Sub

        Private Shared Sub WriteResponse(ByVal sb As StringBuilder, ByVal states As Stack(Of Boolean), ByVal element As XmlElement)
            StartObjectValue(sb, states)
            Dim child As XmlElement
            For Each child In Children(element)
                If LocalName(child) = "base64" Then
                    Key(sb, states, "base64")
                    WriteScalar(sb, "base64", DirectText(child))
                End If
            Next
            EndObject(sb, states)
        End Sub

        Private Shared Sub WriteAttributes(ByVal sb As StringBuilder, ByVal states As Stack(Of Boolean), ByVal element As XmlElement)
            Dim names As New SortedDictionary(Of String, String)()
            Dim node As XmlNode
            For Each node In element.Attributes
                Dim attr As XmlAttribute = CType(node, XmlAttribute)
                Dim name As String = attr.Name
                Dim colon As Integer = name.IndexOf(":"c)
                If colon >= 0 Then
                    name = name.Substring(colon + 1)
                End If
                If name.StartsWith("xmlns") OrElse name = "schemaLocation" Then
                    Continue For
                End If
                names(JsonKey(name)) = attr.Value
            Next
            Dim entry As KeyValuePair(Of String, String)
            For Each entry In names
                Key(sb, states, entry.Key)
                WriteScalar(sb, XmlNameForJsonKey(entry.Key), entry.Value)
            Next
        End Sub

        ''' <summary>
        ''' WriteScalar still classifies by the XML attribute name. Sorted keys are JSON names.
        ''' </summary>
        Private Shared Function XmlNameForJsonKey(ByVal jsonKey As String) As String
            Select Case jsonKey
                Case "preSaveMacro"
                    Return "pre-save-macro"
                Case "postSaveMacro"
                    Return "post-save-macro"
                Case "postMacro"
                    Return "post-macro"
                Case "localDebug"
                    Return "local-debug"
                Case "cellAlign"
                    Return "cell-align"
                Case "cellWidth"
                    Return "cell-width"
                Case Else
                    Return jsonKey
            End Select
        End Function

        Private Shared Function JsonKey(ByVal xmlAttr As String) As String
            Select Case xmlAttr
                Case "pre-save-macro"
                    Return "preSaveMacro"
                Case "post-save-macro"
                    Return "postSaveMacro"
                Case "post-macro", "post-set-macro"
                    Return "postMacro"
                Case "local-debug"
                    Return "localDebug"
                Case "cell-align"
                    Return "cellAlign"
                Case "cell-width"
                    Return "cellWidth"
                Case Else
                    Return xmlAttr
            End Select
        End Function

        Private Shared Sub WriteScalar(ByVal sb As StringBuilder, ByVal xmlName As String, ByVal raw As String)
            If xmlName = "files" OrElse xmlName = "database" OrElse xmlName = "atend" OrElse xmlName = "local-debug" OrElse xmlName = "base64" Then
                If String.Equals(raw, "true", StringComparison.OrdinalIgnoreCase) Then
                    sb.Append("true")
                    Return
                End If
                If String.Equals(raw, "false", StringComparison.OrdinalIgnoreCase) Then
                    sb.Append("false")
                    Return
                End If
            End If
            Dim number As Integer
            If (xmlName = "width" OrElse xmlName = "height") AndAlso Integer.TryParse(raw, Globalization.NumberStyles.Integer, Globalization.CultureInfo.InvariantCulture, number) Then
                sb.Append(number.ToString())
                Return
            End If
            StringValue(sb, raw)
        End Sub

        Private Shared Function Children(ByVal parent As XmlElement) As List(Of XmlElement)
            Dim list As New List(Of XmlElement)()
            Dim node As XmlNode
            For Each node In parent.ChildNodes
                If node.NodeType = XmlNodeType.Element Then
                    list.Add(CType(node, XmlElement))
                End If
            Next
            Return list
        End Function

        Private Shared Function DirectText(ByVal element As XmlElement) As String
            Dim sb As New StringBuilder()
            Dim node As XmlNode
            For Each node In element.ChildNodes
                If node.NodeType = XmlNodeType.Text OrElse node.NodeType = XmlNodeType.CDATA Then
                    sb.Append(node.Value)
                End If
            Next
            Return sb.ToString().Trim()
        End Function

        Private Shared Function LocalName(ByVal element As XmlElement) As String
            If element.LocalName IsNot Nothing AndAlso element.LocalName.Length > 0 Then
                Return element.LocalName
            End If
            Dim name As String = element.Name
            Dim colon As Integer = name.IndexOf(":"c)
            If colon >= 0 Then
                Return name.Substring(colon + 1)
            End If
            Return name
        End Function

        Private Shared Sub BeginValue(ByVal sb As StringBuilder, ByVal states As Stack(Of Boolean))
            If states.Count > 0 Then
                Dim needsComma As Boolean = states.Pop()
                If needsComma Then
                    sb.Append(","c)
                End If
                states.Push(True)
            End If
        End Sub

        Private Shared Sub StartObjectValue(ByVal sb As StringBuilder, ByVal states As Stack(Of Boolean))
            sb.Append("{"c)
            states.Push(False)
        End Sub

        Private Shared Sub StartObjectElement(ByVal sb As StringBuilder, ByVal states As Stack(Of Boolean))
            BeginValue(sb, states)
            sb.Append("{"c)
            states.Push(False)
        End Sub

        Private Shared Sub EndObject(ByVal sb As StringBuilder, ByVal states As Stack(Of Boolean))
            states.Pop()
            sb.Append("}"c)
        End Sub

        Private Shared Sub StartArrayValue(ByVal sb As StringBuilder, ByVal states As Stack(Of Boolean))
            sb.Append("["c)
            states.Push(False)
        End Sub

        Private Shared Sub EndArray(ByVal sb As StringBuilder, ByVal states As Stack(Of Boolean))
            states.Pop()
            sb.Append("]"c)
        End Sub

        Private Shared Sub Key(ByVal sb As StringBuilder, ByVal states As Stack(Of Boolean), ByVal name As String)
            BeginValue(sb, states)
            sb.Append(Quote(name))
            sb.Append(":"c)
        End Sub

        Private Shared Sub StringValue(ByVal sb As StringBuilder, ByVal value As String)
            sb.Append(Quote(If(value, "")))
        End Sub

        Private Shared Function Quote(ByVal value As String) As String
            Dim sb As New StringBuilder()
            sb.Append(""""c)
            Dim i As Integer
            For i = 0 To value.Length - 1
                Dim ch As Char = value.Chars(i)
                Select Case ch
                    Case """"c
                        sb.Append("\""")
                    Case "\"c
                        sb.Append("\\")
                    Case ChrW(8)
                        sb.Append("\b")
                    Case ChrW(12)
                        sb.Append("\f")
                    Case ChrW(10)
                        sb.Append("\n")
                    Case ChrW(13)
                        sb.Append("\r")
                    Case ChrW(9)
                        sb.Append("\t")
                    Case Else
                        If AscW(ch) < 32 Then
                            sb.Append("\u")
                            sb.Append(AscW(ch).ToString("x4"))
                        Else
                            sb.Append(ch)
                        End If
                End Select
            Next
            sb.Append(""""c)
            Return sb.ToString()
        End Function

    End Class

End Namespace
