set pagination off
set confirm off
set logging file /evidence/ai-level-dynamic-trace.txt
set logging overwrite off
set logging redirect on
set logging on
handle SIGUSR1 nostop noprint pass
handle SIGUSR2 nostop noprint pass
printf "ATTACHED executable FantaTennis.exe exact-match-breakpoint=0x004f9c65\n"
break *0x004f9c65
condition 1 *(int *)($ebp+16) >= 0
commands
  silent
  printf "AI_PROFILE_MATCH requested_level=%d matched_level=%d pet_model=%d record=%p destination=%p\n", *(int *)($ebp+8), $ecx, *(int *)($ebp+16), $ebx, *(void **)($ebp+12)
  x/22wx $ebx
  detach
  quit
end
continue
