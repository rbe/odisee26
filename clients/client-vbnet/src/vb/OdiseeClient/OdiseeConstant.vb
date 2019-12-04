'
' Odisee(R)
' Copyright (C) 2011-2013 art of coding UG, http://www.art-of-coding.eu
' Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
'

''' <summary>
''' Constants for Odisee.
''' </summary>
''' <remarks></remarks>
Public Class OdiseeConstant

    ''' <summary>
    ''' XPath for last request below &lt;odisee> root element.
    ''' </summary>
    ''' <remarks></remarks>
    Public Shared ReadOnly LAST_REQUEST As String = "//request[last()]"

    ''' <summary>
    ''' XPath to select last instruction of a request element. Use when you 'have' the request element.
    ''' </summary>
    ''' <remarks></remarks>
    Public Shared ReadOnly LAST_INSTRUCTION_OF_REQUEST As String = "instructions[last()]"

    ''' <summary>
    ''' XPath to select last instruction of a post-pocress element. Use when you 'have' the post-process element.
    ''' </summary>
    ''' <remarks></remarks>
    Public Shared ReadOnly LAST_INSTRUCTION_OF_POSTPROCESS As String = "instructions[last()]"

    ''' <summary>
    ''' XPath for a selecting a request by name.
    ''' </summary>
    ''' <remarks></remarks>
    Public Shared ReadOnly REQUEST_NAME As String = "//request[@name]"

    ''' <summary>
    ''' Request cannot be processed, no service URL given.
    ''' </summary>
    ''' <remarks></remarks>
    Public Shared ReadOnly ERR_NO_SERVICE_URL As String = "Cannot process request, no Odisee service URL"

    ''' <summary>
    ''' Request cannot be processed. No authentication information, no username or and/or no password.
    ''' </summary>
    ''' <remarks></remarks>
    Public Shared ReadOnly ERR_NO_AUTH_INFO As String = "Cannot process request, insufficient auth info"

End Class
