use strict;
use warnings;
use utf8;

sub read_file {
    my ($path) = @_;
    open my $fh, '<:raw', $path or die "open $path: $!";
    local $/;
    my $data = <$fh>;
    close $fh;
    return $data;
}
sub write_file {
    my ($path, $data) = @_;
    open my $fh, '>:raw', $path or die "write $path: $!";
    print {$fh} $data or die "write $path: $!";
    close $fh or die "close $path: $!";
}
sub replace_once {
    my ($text, $old, $new, $label) = @_;
    my $at = index($text, $old);
    die "Android guest patch target missing: $label\n" if $at < 0;
    die "Android guest patch target duplicated: $label\n" if index($text, $old, $at + length($old)) >= 0;
    substr($text, $at, length($old), $new);
    return $text;
}

my $shell_path = 'src/museum/shell.nas';
my $shell = read_file($shell_path);
$shell =~ s/^sh_after_prog:\n.*?^JMP \@sh_redraw$/sh_after_prog:\nLA r10,\@boot_rec\nJMP \@sh_run/ms
    or die "Android desktop return patch target missing\n";
my $cold = "LA r10,\@boot_rec\nLD r1,r10,0\nCMP r1,r0\nJMP[EQ] \@sh_redraw\nJMP \@sh_run";
$shell = replace_once($shell, $cold, "LA r10,\@boot_rec\nJMP \@sh_run", "disable museum menu cold-boot fallback");
write_file($shell_path, $shell);


my $wm_path = 'src/lib/wm.nas';
my $wm = read_file($wm_path);
$wm = replace_once($wm, '.equ TOPH 17', '.equ TOPH 44', 'desktop top strip height');
$wm = replace_once($wm, "CALL \@fb_text7\nST r0,r0,\@da_i",
    "CALL \@fb_text7\nCALL \@wm_fkeybar\nST r0,r0,\@da_i", "draw NaN16 F-key strip");
$wm = replace_once($wm, 'F7 SYSTEM', 'F7 SETTINGS', 'label F7 as unified Settings');
$wm = replace_once($wm, 'F7 SYS', 'F7 SET', 'label compact F7 hint as Settings');
write_file($wm_path, $wm);

my $gui_path = 'src/exhibits/gui_cart.nas';
my $gui = read_file($gui_path);
$gui = replace_once($gui, ".include \"../lib/wm.nas\"", ".include \"../lib/wm.nas\"\n.include \"android_desktop_input.inc\"", "include NaN16DE guest pointer/window controls");
$gui = replace_once($gui, "CALL \@pci_scan\nCALL \@pci_nic",
    "CALL \@wm_wbase\nMOVI r2,3\nST r2,r1,#W_A3", "replace guest PCI NaNNet probe");
$gui =~ s/\.str "NaNNet  NETWORK CONTROL  \(DRIVER PENDING\)"/.str "NaNNet  ANDROID NETWORK MANAGER"/
    or die "NaNNet title patch target missing\n";
$gui =~ s/\.str "ADAPTER"/.str "NETWORK"/ or die "NaNNet adapter label missing\n";
$gui =~ s/\.str "NONE ON PCI BUS 0"/.str "ANDROID WIFI \/ CELLULAR ROUTE"/
    or die "NaNNet PCI status patch target missing\n";
$gui =~ s/\.str "NO ADAPTER FOUND"/.str "HOST NETWORK VIA ANDROID"/
    or die "NaNNet no-adapter text patch target missing\n";
$gui =~ s/\.str "ADAPTER FOUND, NO DRIVER YET \(STUB\)"/.str "ANDROID HOST NETWORK ACTIVE"/
    or die "NaNNet stub status patch target missing\n";
die "Android NaNNet still contains PCI-only status\n"
    if $gui =~ /NONE ON PCI BUS 0|NO ADAPTER FOUND/;

my $boot = join "\n", "MOVI r1,0", "CALL \@gui_open_new", "MOVI r1,1", "ST r1,r0,\@wm_redraw";
$gui = replace_once($gui, $boot,
    "MOVI r1,0\nCALL \@gui_open_new\nMOVI r1,1\nST r1,r0,\@wm_redraw",
    "boot desktop with NSH as its first child window");

my $browser_start = index($gui, "br_show:\n");
my $browser_end = index($gui, "br_init:\n", $browser_start);
die "Android browser patch boundaries missing\n" if $browser_start < 0 || $browser_end < 0;
my $browser = <<'NAS';
br_show:
ST r15,r0,@bs_ret
LD r1,r0,@br_id
CALL @g_clr
LD r1,r0,@br_id
CALL @tx_org
MOVI r1,0
MOVI r2,0
MOVI r3,@br_tabs
MOVI r4,#C_CY
CALL @tx_s
MOVI r1,0
MOVI r2,2
MOVI r3,@br_status
MOVI r4,#C_DIM
CALL @tx_s
LD r1,r0,@br_tab
SHL r1,r1,2
MOVI r2,@br_pages
ADD r1,r1,r2
LD r3,r1,0
LD r1,r0,@br_id
MOVI r2,4
MOVI r4,#C_TXT
CALL @tx_s
LD r15,r0,@bs_ret
RET
NAS
substr($gui, $browser_start, $browser_end - $browser_start, $browser);
$gui = replace_once($gui, "CALL \@term_fit\nMOVI r1,0\nST r1,r0,\@br_tab",
    "MOVI r1,0\nST r1,r0,\@br_tab", "remove terminal redraw from browser init");

my $loop_exit = join "\n", "LD r1,r0,\@wm_zn", "CMP r1,r0", "JMP[EQ] \@g_exit", "CALL \@kbd_poll";
$gui = replace_once($gui, $loop_exit, "CALL \@wm_pointer_poll\nCALL \@kbd_poll", "keep desktop alive and dispatch guest pointer events");
my $old_nsh = join "\n", "tm_nsh:", "MOVI r1,4800", "MOVI r2,10", "ST r2,r1,0", "MOVI r1,1", "ST r1,r0,\@g_quit";
my $new_nsh = join "\n", "tm_nsh:", "MOVI r1,0", "CALL \@gui_single";
$gui = replace_once($gui, $old_nsh, $new_nsh, "route NSH to desktop terminal child");
$gui = replace_once($gui,
    "tm_term:\nMOVI r1,0\nCALL \@gui_open_new\nJMP \@tm_done",
    "tm_term:\nMOVI r1,0\nCALL \@gui_single\nJMP \@tm_done",
    "make the terminal command raise the single NSH child window");
$gui = replace_once($gui,
    "tm_quit:\nMOVI r1,1\nST r1,r0,\@g_quit\nJMP \@tm_done",
    "tm_quit:\nLD r1,r0,\@tm_id\nCALL \@wm_close\nJMP \@tm_done",
    "close the NSH child window without exiting the desktop");
$gui = replace_once($gui, '.str "TERMINAL"', '.str "NSH TERMINAL"', "NSH window title");
my $old_banner = "s_banner:\n.str \"NaN/0S TERMINAL\\nh = help\\n> \"";
my $new_banner = "s_banner:\n.str \"NaNLMind NSH\\n\"";
$gui = replace_once($gui, $old_banner, $new_banner, "replace boot NSH banner without losing prompt");
my $boot_banner = join "\n", 'MOVI r2,@s_banner', 'CALL @vc_puts';
my $boot_help = join "\n",
    'MOVI r2,@s_banner', 'CALL @vc_puts',
    'LD r1,r0,@ti_id', 'MOVI r2,@s_help', 'CALL @vc_puts',
    'LD r1,r0,@ti_id', 'MOVI r2,@s_prompt', 'CALL @vc_puts';
$gui = replace_once($gui, $boot_banner, $boot_help,
    "print full --help reference and a fresh prompt in the boot NSH window");
$gui = replace_once($gui, '.str "MHZ"', '.str "MIPS"', "label VM instruction throughput honestly");
my $settings_host = join "\n", "tm_set:", "MOVI r1,78", "PUTC r1", "MOVI r1,49", "PUTC r1",
    "MOVI r1,54", "PUTC r1", "MOVI r1,72", "PUTC r1", "MOVI r1,79", "PUTC r1",
    "MOVI r1,83", "PUTC r1", "MOVI r1,84", "PUTC r1", "MOVI r1,58", "PUTC r1",
    "MOVI r1,83", "PUTC r1", "MOVI r1,69", "PUTC r1", "MOVI r1,84", "PUTC r1",
    "MOVI r1,84", "PUTC r1", "MOVI r1,73", "PUTC r1", "MOVI r1,78", "PUTC r1",
    "MOVI r1,71", "PUTC r1", "MOVI r1,83", "PUTC r1", "MOVI r1,10", "PUTC r1",
    'JMP @tm_done';
$gui = replace_once($gui, "tm_set:\nMOVI r1,4\nCALL \@gui_single\nJMP \@tm_done",
    $settings_host, "route NSH settings command to unified Android desktop settings");
my $wifi_host = join "\n", "tm_net:", "MOVI r1,78", "PUTC r1", "MOVI r1,49", "PUTC r1",
    "MOVI r1,54", "PUTC r1", "MOVI r1,72", "PUTC r1", "MOVI r1,79", "PUTC r1",
    "MOVI r1,83", "PUTC r1", "MOVI r1,84", "PUTC r1", "MOVI r1,58", "PUTC r1",
    "MOVI r1,87", "PUTC r1", "MOVI r1,73", "PUTC r1", "MOVI r1,70", "PUTC r1",
    "MOVI r1,73", "PUTC r1", "MOVI r1,10", "PUTC r1", 'JMP @tm_done';
$gui = replace_once($gui, "tm_net:\nMOVI r1,7\nCALL \@gui_open_new\nJMP \@tm_done",
    $wifi_host, "route NSH NaNNet command to Android Wi-Fi/cellular manager");
my $browser_host = join "\n", "tm_br:", "MOVI r1,78", "PUTC r1", "MOVI r1,49", "PUTC r1",
    "MOVI r1,54", "PUTC r1", "MOVI r1,72", "PUTC r1", "MOVI r1,79", "PUTC r1",
    "MOVI r1,83", "PUTC r1", "MOVI r1,84", "PUTC r1", "MOVI r1,58", "PUTC r1",
    "MOVI r1,66", "PUTC r1", "MOVI r1,82", "PUTC r1", "MOVI r1,79", "PUTC r1",
    "MOVI r1,87", "PUTC r1", "MOVI r1,83", "PUTC r1", "MOVI r1,69", "PUTC r1",
    "MOVI r1,82", "PUTC r1", "MOVI r1,10", "PUTC r1", 'JMP @tm_done';
$gui = replace_once($gui, "tm_br:\nMOVI r1,7\nCALL \@gui_open_new\nJMP \@tm_done",
    $browser_host, "route NSH browser command to isolated Android browser launcher");
$gui =~ s/TSC over >= 500 ms of TICK -> MHz/guest instruction steps over >= 500 ms of TICK -> MIPS/
    or die "Android VM throughput label comment missing\n";
$gui = replace_once($gui, 'f files (nsh) q quit gui\n', 'f open terminal  q close desktop\n', "desktop terminal hint");
$gui = replace_once($gui, 's settings n browser u utils\n', 'n browser u utils\n', "remove duplicate guest settings menu");
my $command_token = join "\n",
    "ADD r5,r1,r5",
    "LD r4,r5,0", "MOVI r7,101", "CMP r4,r7", 'JMP[NE] @tk_check_quit',
    "ADDI r6,r5,1", "LD r4,r6,0", "MOVI r7,120", "CMP r4,r7", 'JMP[NE] @tk_check_quit',
    "ADDI r6,r5,2", "LD r4,r6,0", "MOVI r7,105", "CMP r4,r7", 'JMP[NE] @tk_check_quit',
    "ADDI r6,r5,3", "LD r4,r6,0", "MOVI r7,116", "CMP r4,r7", 'JMP[NE] @tk_check_quit',
    "MOVI r4,113", 'JMP @tk_lc',
    "tk_check_quit:",
    "LD r4,r5,0", "MOVI r7,113", "CMP r4,r7", 'JMP[NE] @tk_check_files',
    "ADDI r6,r5,1", "LD r4,r6,0", "MOVI r7,117", "CMP r4,r7", 'JMP[NE] @tk_check_files',
    "ADDI r6,r5,2", "LD r4,r6,0", "MOVI r7,105", "CMP r4,r7", 'JMP[NE] @tk_check_files',
    "ADDI r6,r5,3", "LD r4,r6,0", "MOVI r7,116", "CMP r4,r7", 'JMP[NE] @tk_check_files',
    "MOVI r4,113", 'JMP @tk_lc',
    "tk_check_files:",
    "LD r4,r5,0", "MOVI r7,102", "CMP r4,r7", 'JMP[NE] @tk_check_write',
    "ADDI r6,r5,1", "LD r4,r6,0", "MOVI r7,105", "CMP r4,r7", 'JMP[NE] @tk_check_write',
    "ADDI r6,r5,2", "LD r4,r6,0", "MOVI r7,108", "CMP r4,r7", 'JMP[NE] @tk_check_write',
    "ADDI r6,r5,3", "LD r4,r6,0", "MOVI r7,101", "CMP r4,r7", 'JMP[NE] @tk_check_write',
    "ADDI r6,r5,4", "LD r4,r6,0", "MOVI r7,115", "CMP r4,r7", 'JMP[NE] @tk_check_write',
    "MOVI r1,78", "PUTC r1", "MOVI r1,49", "PUTC r1",
    "MOVI r1,54", "PUTC r1", "MOVI r1,72", "PUTC r1",
    "MOVI r1,79", "PUTC r1", "MOVI r1,83", "PUTC r1",
    "MOVI r1,84", "PUTC r1", "MOVI r1,58", "PUTC r1",
    "MOVI r1,70", "PUTC r1", "MOVI r1,73", "PUTC r1",
    "MOVI r1,76", "PUTC r1", "MOVI r1,69", "PUTC r1",
    "MOVI r1,83", "PUTC r1", "MOVI r1,10", "PUTC r1",
    "MOVI r4,0", 'JMP @tk_lc',
    "tk_check_write:",
    "LD r4,r5,0", "MOVI r7,119", "CMP r4,r7", 'JMP[NE] @tk_check_training',
    "ADDI r6,r5,1", "LD r4,r6,0", "MOVI r7,114", "CMP r4,r7", 'JMP[NE] @tk_check_training',
    "ADDI r6,r5,2", "LD r4,r6,0", "MOVI r7,105", "CMP r4,r7", 'JMP[NE] @tk_check_training',
    "ADDI r6,r5,3", "LD r4,r6,0", "MOVI r7,116", "CMP r4,r7", 'JMP[NE] @tk_check_training',
    "ADDI r6,r5,4", "LD r4,r6,0", "MOVI r7,101", "CMP r4,r7", 'JMP[NE] @tk_check_training',
    "ADDI r6,r5,5", "LD r4,r6,0", "MOVI r7,32", "CMP r4,r7", 'JMP[NE] @tk_check_n16t',
    "MOVI r1,78", "PUTC r1", "MOVI r1,49", "PUTC r1",
    "MOVI r1,54", "PUTC r1", "MOVI r1,72", "PUTC r1",
    "MOVI r1,79", "PUTC r1", "MOVI r1,83", "PUTC r1",
    "MOVI r1,84", "PUTC r1", "MOVI r1,58", "PUTC r1",
    "MOVI r1,87", "PUTC r1", "MOVI r1,82", "PUTC r1",
    "MOVI r1,73", "PUTC r1", "MOVI r1,84", "PUTC r1",
    "MOVI r1,69", "PUTC r1", "MOVI r1,58", "PUTC r1",
    "ADDI r6,r5,6",
    "tk_write_loop:", "LD r4,r6,0", "CMP r4,r0", 'JMP[EQ] @tk_write_done',
    "MOVI r7,32", "CMP r4,r7", 'JMP[LE] @tk_write_done',
    "PUTC r4", "ADDI r6,r6,1", 'JMP @tk_write_loop',
    "tk_write_done:", "MOVI r1,10", "PUTC r1", "MOVI r4,0", 'JMP @tk_lc',
    "tk_check_n16t:",
    "LD r4,r5,0", "MOVI r7,110", "CMP r4,r7", 'JMP[NE] @tk_check_training',
    "ADDI r6,r5,1", "LD r4,r6,0", "MOVI r7,49", "CMP r4,r7", 'JMP[NE] @tk_check_training',
    "ADDI r6,r5,2", "LD r4,r6,0", "MOVI r7,54", "CMP r4,r7", 'JMP[NE] @tk_check_training',
    "ADDI r6,r5,3", "LD r4,r6,0", "MOVI r7,116", "CMP r4,r7", 'JMP[NE] @tk_check_training',
    "MOVI r1,78", "PUTC r1", "MOVI r1,49", "PUTC r1",
    "MOVI r1,54", "PUTC r1", "MOVI r1,72", "PUTC r1",
    "MOVI r1,79", "PUTC r1", "MOVI r1,83", "PUTC r1",
    "MOVI r1,84", "PUTC r1", "MOVI r1,58", "PUTC r1",
    "MOVI r1,71", "PUTC r1", "MOVI r1,65", "PUTC r1",
    "MOVI r1,84", "PUTC r1", "MOVI r1,69", "PUTC r1",
    "MOVI r1,87", "PUTC r1", "MOVI r1,65", "PUTC r1",
    "MOVI r1,89", "PUTC r1", "MOVI r1,10", "PUTC r1",
    "MOVI r4,0", 'JMP @tk_lc',
    "tk_check_training:",
    "LD r4,r5,0", "MOVI r7,110", "CMP r4,r7", 'JMP[NE] @tk_help_check',
    "ADDI r6,r5,1", "LD r4,r6,0", "MOVI r7,97", "CMP r4,r7", 'JMP[NE] @tk_help_check',
    "ADDI r6,r5,2", "LD r4,r6,0", "MOVI r7,110", "CMP r4,r7", 'JMP[NE] @tk_help_check',
    "ADDI r6,r5,3", "LD r4,r6,0", "MOVI r7,110", "CMP r4,r7", 'JMP[NE] @tk_help_check',
    "ADDI r6,r5,4", "LD r4,r6,0", "MOVI r7,101", "CMP r4,r7", 'JMP[NE] @tk_help_check',
    "ADDI r6,r5,5", "LD r4,r6,0", "MOVI r7,116", "CMP r4,r7", 'JMP[NE] @tk_help_check',
    "ADDI r6,r5,6", "LD r4,r6,0", "MOVI r7,45", "CMP r4,r7", 'JMP[NE] @tk_help_check',
    "ADDI r6,r5,7", "LD r4,r6,0", "MOVI r7,116", "CMP r4,r7", 'JMP[NE] @tk_help_check',
    "ADDI r6,r5,8", "LD r4,r6,0", "MOVI r7,114", "CMP r4,r7", 'JMP[NE] @tk_help_check',
    "ADDI r6,r5,9", "LD r4,r6,0", "MOVI r7,97", "CMP r4,r7", 'JMP[NE] @tk_help_check',
    "ADDI r6,r5,10", "LD r4,r6,0", "MOVI r7,105", "CMP r4,r7", 'JMP[NE] @tk_help_check',
    "ADDI r6,r5,11", "LD r4,r6,0", "MOVI r7,110", "CMP r4,r7", 'JMP[NE] @tk_help_check',
    "ADDI r6,r5,12", "LD r4,r6,0", "MOVI r7,105", "CMP r4,r7", 'JMP[NE] @tk_help_check',
    "ADDI r6,r5,13", "LD r4,r6,0", "MOVI r7,110", "CMP r4,r7", 'JMP[NE] @tk_help_check',
    "ADDI r6,r5,14", "LD r4,r6,0", "MOVI r7,103", "CMP r4,r7", 'JMP[NE] @tk_help_check',
    "MOVI r1,78", "PUTC r1", "MOVI r1,49", "PUTC r1",
    "MOVI r1,54", "PUTC r1", "MOVI r1,72", "PUTC r1",
    "MOVI r1,79", "PUTC r1", "MOVI r1,83", "PUTC r1",
    "MOVI r1,84", "PUTC r1", "MOVI r1,58", "PUTC r1",
    "MOVI r1,84", "PUTC r1", "MOVI r1,82", "PUTC r1",
    "MOVI r1,65", "PUTC r1", "MOVI r1,73", "PUTC r1",
    "MOVI r1,78", "PUTC r1", "MOVI r1,10", "PUTC r1",
    "MOVI r4,0", 'JMP @tk_lc',
    "tk_help_check:",
    "LD r4,r5,0", "MOVI r7,45", "CMP r4,r7", 'JMP[NE] @tk_firstchar',
    "ADDI r6,r5,1", "LD r4,r6,0", "CMP r4,r7", 'JMP[NE] @tk_firstchar',
    "ADDI r6,r6,1", "LD r4,r6,0", "MOVI r7,104", "CMP r4,r7", 'JMP[NE] @tk_firstchar',
    "ADDI r6,r6,1", "LD r4,r6,0", "MOVI r7,101", "CMP r4,r7", 'JMP[NE] @tk_firstchar',
    "ADDI r6,r6,1", "LD r4,r6,0", "MOVI r7,108", "CMP r4,r7", 'JMP[NE] @tk_firstchar',
    "ADDI r6,r6,1", "LD r4,r6,0", "MOVI r7,112", "CMP r4,r7", 'JMP[NE] @tk_firstchar',
    "MOVI r4,104", 'JMP @tk_lc',
    "tk_firstchar:", "LD r4,r5,0";
$gui = replace_once($gui, "ADD r5,r1,r5\nLD r4,r5,0", $command_token,
    "recognize exit, quit, nannet-training and --help as NaN16 NSH commands");
my $new_help = '.str "NSH --help / h HELP  c CLEAR  v VERSION  w WINDOWS\nb BROWSER  l LATTICE  m MONITOR  x HEX  t RAISE NSH\nf FOCUS NSH  s SETTINGS  n NANNET/WI-FI  u UTILITIES\nfiles LIST FILES  write NAME OPEN TEXT EDITOR\nn16t GATEWAY  nannet-training TRAINING\nF5 SETTINGS  F6 NANNET  F7 SETTINGS\nexit / quit / q  CLOSE NSH WINDOW\n"';
$gui =~ s/(s_help:\n)\.str "[^"]*"/$1$new_help/s
    or die "Android NSH help text patch target missing\n";


my $marker = "gk_np:\nMOVI r2,0x105";
my $insert = join "\n",
    "gk_np:", "MOVI r2,0x108", "CMP r1,r2", "JMP[NE] \@gk_n9",
    "MOVI r1,1", "CALL \@gui_single", "JMP \@gk_ret",
    "gk_n9:", "MOVI r2,0x109", "CMP r1,r2", "JMP[NE] \@gk_n10",
    "MOVI r1,2", "CALL \@gui_single", "JMP \@gk_ret",
    "gk_n10:", "MOVI r2,0x10A", "CMP r1,r2", "JMP[NE] \@gk_f5",
    "MOVI r1,3", "CALL \@gui_single", "JMP \@gk_ret",
    "gk_f5:", "MOVI r2,0x105";
$gui = replace_once($gui, $marker, $insert, "desktop global app hotkeys");


my $settings_hotkey = join "\n",
    'MOVI r1,78', 'PUTC r1', 'MOVI r1,49', 'PUTC r1',
    'MOVI r1,54', 'PUTC r1', 'MOVI r1,72', 'PUTC r1',
    'MOVI r1,79', 'PUTC r1', 'MOVI r1,83', 'PUTC r1',
    'MOVI r1,84', 'PUTC r1', 'MOVI r1,58', 'PUTC r1',
    'MOVI r1,83', 'PUTC r1', 'MOVI r1,69', 'PUTC r1',
    'MOVI r1,84', 'PUTC r1', 'MOVI r1,84', 'PUTC r1',
    'MOVI r1,73', 'PUTC r1', 'MOVI r1,78', 'PUTC r1',
    'MOVI r1,71', 'PUTC r1', 'MOVI r1,83', 'PUTC r1',
    'MOVI r1,10', 'PUTC r1', 'JMP @gk_ret';
my $wifi_hotkey = join "\n",
    'MOVI r1,78', 'PUTC r1', 'MOVI r1,49', 'PUTC r1',
    'MOVI r1,54', 'PUTC r1', 'MOVI r1,72', 'PUTC r1',
    'MOVI r1,79', 'PUTC r1', 'MOVI r1,83', 'PUTC r1',
    'MOVI r1,84', 'PUTC r1', 'MOVI r1,58', 'PUTC r1',
    'MOVI r1,87', 'PUTC r1', 'MOVI r1,73', 'PUTC r1',
    'MOVI r1,70', 'PUTC r1', 'MOVI r1,73', 'PUTC r1',
    'MOVI r1,10', 'PUTC r1', 'JMP @gk_ret';
my $f6 = join "\n", 'gk_n5:', 'MOVI r2,0x106', 'CMP r1,r2',
    'JMP[NE] @gk_n6', 'MOVI r1,5', 'CALL @gui_single', 'JMP @gk_ret';
$gui = replace_once($gui, $f6,
    join("\n", 'gk_n5:', 'MOVI r2,0x106', 'CMP r1,r2',
         'JMP[NE] @gk_n6', $wifi_hotkey),
    "F6 opens the same live Android NANNET manager as the n command");

my $f7 = join "\n", 'gk_n6:', 'MOVI r2,0x107', 'CMP r1,r2', 'JMP[NE] @gk_n7', 'MOVI r1,6';
$gui = replace_once($gui, $f7,
    join("\n", 'gk_n6:', 'MOVI r2,0x107', 'CMP r1,r2', 'JMP[NE] @gk_n7', $settings_hotkey),
    "F7 opens the unified Settings surface");
my $f5 = join "\n", 'gk_f5:', 'MOVI r2,0x105', 'CMP r1,r2', 'JMP[NE] @gk_n5',
    'MOVI r1,4', 'CALL @gui_single', 'JMP @gk_ret', 'gk_n5:';
$gui = replace_once($gui, $f5,
    join("\n", 'gk_f5:', 'MOVI r2,0x105', 'CMP r1,r2', 'JMP[NE] @gk_n5',
         $settings_hotkey, 'gk_n5:'),
    "F5 and F7 open the same unified Settings surface");

die "NSH full-command parser missing files/write handlers\n"
    unless index($gui, "tk_check_files:") >= 0 &&
           index($gui, "tk_check_write:") >= 0 &&
           index($gui, "tk_write_loop:") >= 0;
die "NSH full-command parser missing nannet-training\n"
    unless index($gui, "tk_check_training:") >= 0;
die "NSH exit no longer closes only its child window\n"
    unless index($gui, join("\n", 'tm_quit:', 'LD r1,r0,@tm_id', 'CALL @wm_close')) >= 0;
die "F6 does not open the live NANNET manager\n"
    unless index($gui, "gk_n5:") >= 0 && index($gui, $wifi_hotkey) >= 0;
die "F7 does not open the unified Settings window\n"
    unless index($gui, join("\n", 'gk_n6:', 'MOVI r2,0x107', 'CMP r1,r2',
        'JMP[NE] @gk_n7', 'MOVI r1,78', 'PUTC r1')) >= 0;
die "F5 does not open the same unified Settings window\n"
    unless index($gui, join("\n", 'gk_f5:', 'MOVI r2,0x105', 'CMP r1,r2',
        'JMP[NE] @gk_n5', 'MOVI r1,78', 'PUTC r1')) >= 0;
die "NSH help omits the training command\n"
    unless index($gui, "nannet-training TRAINING") >= 0;

$gui .= "\n" . join("\n", 's_prompt:', '.str "> "');
write_file($gui_path, $gui);

my $check_shell = read_file($shell_path);
die "Android shell can still fall into museum menu\n"
    unless $check_shell =~ /sh_after_prog:\nLA r10,\@boot_rec\nJMP \@sh_run/s;
my $museum_fallback = "LA r10,\@boot_rec\nLD r1,r10,0\nCMP r1,r0\nJMP[EQ] \@sh_redraw\nJMP \@sh_run";
die "Android cold boot still branches to museum menu\n"
    if index($check_shell, $museum_fallback) >= 0;
print "Android NaN16 desktop/NSH/browser/NaNNet patch invariants: PASS\n";
