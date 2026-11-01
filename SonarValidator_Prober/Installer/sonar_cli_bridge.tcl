::cisco::eem::event_register_timer watchdog time 2 maxrun 20
namespace import ::cisco::eem::*
namespace import ::cisco::lib::*
set base "bootflash:/guest-share/sonar-cli"
set request "$base/request"
if {![file exists $request]} { exit 0 }
set input [open $request r]
set content [read $input 65536]
close $input
file delete -force $request
set lines [split $content "\n"]
set token [lindex $lines 0]
if {![regexp {^[0-9]+-[0-9]+$} $token]} { exit 0 }
set output ""
set status "OK"
set opened 0
if {[catch {
    array set cli [cli_open]
    set opened 1
    cli_exec $cli(fd) "enable"
    cli_exec $cli(fd) "terminal length 0"
    foreach command [lrange $lines 1 end] {
        set command [string trim $command]
        if {[string equal $command ""]} { continue }
        if {![regexp -nocase {^(show [a-z0-9 _./|:-]+|configure terminal|interface [a-z0-9./_-]+|shutdown|no shutdown|(no )?ip access-list extended SONAR-[a-z0-9_-]+|(no )?ip access-group SONAR-[a-z0-9_-]+ in|deny ip [0-9.]+ [0-9.]+ [0-9.]+ [0-9.]+|permit ip any any|end|exit)$} $command]} {
            error "Command rejected by EEM bridge"
        }
        append output [cli_exec $cli(fd) $command] "\n"
    }
} result]} {
    set status "ERROR"
    append output $result "\n"
}
if {$opened} { catch {cli_close $cli(fd) $cli(tty_id)} }
set response [open "$base/response" w]
puts $response $token
puts $response $status
puts $response $output
puts $response "END $token"
close $response
