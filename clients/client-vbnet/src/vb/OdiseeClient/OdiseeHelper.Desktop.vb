'
' Odisee(R)
' Copyright (C) 2011-2013 art of coding UG, http://www.art-of-coding.eu
' Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
'

Option Strict On
Option Explicit On

Namespace Helper

    ''' <summary>
    ''' Helper for desktop applications.
    ''' </summary>
    ''' <remarks></remarks>
    Public Class Desktop

        ''' <summary>
        ''' Open an URL, e.g. http://... in a browser.
        ''' </summary>
        ''' <param name="url"></param>
        ''' <remarks></remarks>
        Public Shared Sub openURL(ByVal url As String)
            System.Diagnostics.Process.Start(url)
        End Sub

    End Class

End Namespace
