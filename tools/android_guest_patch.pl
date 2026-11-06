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
$gui = replace_once($gui, $boot, "MOVI r1,1\nST r1,r0,\@wm_redraw", "boot desktop without modal duplicate terminal");

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
$gui = replace_once($gui, '.str "TERMINAL"', '.str "NSH TERMINAL"', "NSH window title");
$gui = replace_once($gui, '.str "MHZ"', '.str "MIPS"', "label VM instruction throughput honestly");
$gui =~ s/TSC over >= 500 ms of TICK -> MHz/guest instruction steps over >= 500 ms of TICK -> MIPS/
    or die "Android VM throughput label comment missing\n";
$gui = replace_once($gui, 'f files (nsh) q quit gui\n', 'f open terminal  q close desktop\n', "desktop terminal hint");
$gui = replace_once($gui, 's settings n browser u utils\n', 'n browser u utils\n', "remove duplicate guest settings menu");
my $help_token = join "\\n",
    "ADD r5,r1,r5",
    "LD r4,r5,0", "MOVI r7,45", "CMP r4,r7", "JMP[NE] \\@tk_firstchar",
    "ADD r6,r5,r13", "LD r4,r6,0", "CMP r4,r7", "JMP[NE] \\@tk_firstchar",
    "ADD r6,r6,r13", "LD r4,r6,0", "MOVI r7,104", "CMP r4,r7", "JMP[NE] \\@tk_firstchar",
    "ADD r6,r6,r13", "LD r4,r6,0", "MOVI r7,101", "CMP r4,r7", "JMP[NE] \\@tk_firstchar",
    "ADD r6,r6,r13", "LD r4,r6,0", "MOVI r7,108", "CMP r4,r7", "JMP[NE] \\@tk_firstchar",
    "ADD r6,r6,r13", "LD r4,r6,0", "MOVI r7,112", "CMP r4,r7", "JMP[NE] \\@tk_firstchar",
    "MOVI r4,104", "JMP \\@tk_lc",
    "tk_firstchar:", "LD r4,r5,0";
$gui = replace_once($gui, "ADD r5,r1,r5\\nLD r4,r5,0", $help_token,
    "recognize the complete --help token in NaN16 NSH");

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

my $f7 = join "\n", "gk_n6:", "MOVI r2,0x107", "CMP r1,r2", "JMP[NE] \@gk_n7", "MOVI r1,6";
$gui = replace_once($gui, $f7,
    join("\n", "gk_n6:", "MOVI r2,0x107", "CMP r1,r2", "JMP[NE] \@gk_n7", "MOVI r1,4"),
    "F7 opens the guest desktop settings window");

write_file($gui_path, $gui);

my $check_shell = read_file($shell_path);
die "Android shell can still fall into museum menu\n"
    unless $check_shell =~ /sh_after_prog:\nLA r10,\@boot_rec\nJMP \@sh_run/s;
my $museum_fallback = "LA r10,\@boot_rec\nLD r1,r10,0\nCMP r1,r0\nJMP[EQ] \@sh_redraw\nJMP \@sh_run";
die "Android cold boot still branches to museum menu\n"
    if index($check_shell, $museum_fallback) >= 0;
print "Android NaN16 desktop/NSH/browser/NaNNet patch invariants: PASS\n";
